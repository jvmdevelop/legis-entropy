import asyncio
from collections import deque
import logging
from pathlib import Path

from aiogram import Bot, Dispatcher, F, Router
from aiogram.client.default import DefaultBotProperties
from aiogram.enums import ParseMode
from aiogram.exceptions import TelegramBadRequest
from aiogram.filters import Command, CommandStart
from aiogram.types import BufferedInputFile, Message

from .backend import ANALYSIS_PROMPT, Backend, BackendError, user_id_for
from .config import Settings
from .formatting import split_message, to_telegram_html
from .live import LiveAnswer, StatusTicker

log = logging.getLogger("legis-bot")

SUPPORTED = {".pdf", ".docx", ".txt"}
HISTORY_ENTRIES = 8
HISTORY_CHARS = 4000

HELP = (
    "<b>Legis Entropy</b> — проверка договоров по законодательству РК.\n\n"
    "📄 Пришлите договор файлом (PDF, DOCX или TXT). Я найду:\n"
    "• ссылки на статьи, которые не соответствуют закону;\n"
    "• нарушения норм (например, отпуск меньше 24 дней);\n"
    "• ссылки на утратившие силу законы.\n"
    "🛠 Если найду ошибки — пришлю исправленный документ (оригинал не меняется).\n\n"
    "💬 После этого можно задавать вопросы по документу обычным сообщением.\n"
    "Без документа отвечу на общий вопрос по законам РК.\n\n"
    "/new — забыть текущий документ\n\n"
    "<i>Текст документа обрабатывается языковой моделью. Не присылайте документы "
    "с данными, которые нельзя передавать третьим лицам. Ответ не заменяет юриста.</i>"
)


async def safe_edit_or_send(first: Message, markdown: str) -> None:
    parts = split_message(to_telegram_html(markdown))
    for i, part in enumerate(parts):
        try:
            if i == 0:
                await first.edit_text(part)
            else:
                await first.answer(part)
        except TelegramBadRequest:
            if i == 0:
                await first.edit_text(part, parse_mode=None)
            else:
                await first.answer(part, parse_mode=None)


def fixed_caption(n: int) -> str:
    return (f"✅ Исправленный документ: внесено правок — {n}. Оригинал не изменён. "
            "Формулировки, предложенные ИИ, проверьте перед подписанием.")


class BotApp:
    def __init__(self, settings: Settings, backend: Backend):
        self.settings = settings
        self.backend = backend
        self.last_document: dict[int, str] = {}
        self.history: dict[int, deque] = {}
        self.router = Router()
        self.router.message.register(self.start, CommandStart())
        self.router.message.register(self.start, Command("help"))
        self.router.message.register(self.new, Command("new"))
        self.router.message.register(self.document, F.document)
        self.router.message.register(self.text, F.text)

    async def start(self, message: Message) -> None:
        await message.answer(HELP)

    async def new(self, message: Message) -> None:
        self.last_document.pop(message.from_user.id, None)
        self.history.pop(message.from_user.id, None)
        await message.answer("Документ забыт. Пришлите новый файл или задайте вопрос.")

    async def document(self, message: Message, bot: Bot) -> None:
        doc = message.document
        suffix = Path(doc.file_name or "").suffix.lower()
        if suffix not in SUPPORTED:
            await message.answer("Поддерживаются PDF, DOCX и TXT.")
            return
        if doc.file_size and doc.file_size > self.settings.max_file_mb * 1024 * 1024:
            await message.answer(f"Файл больше {self.settings.max_file_mb} МБ.")
            return

        user_id = user_id_for(message.from_user.id)
        status_msg = await message.answer("⏳ Загружаю документ…")
        ticker = StatusTicker(status_msg, "Загружаю документ").start()
        try:
            content = (await bot.download(doc)).read()
            document_id = await self.backend.upload(user_id, doc.file_name, content)
            ticker.stage = "Читаю и индексирую документ"
            await self.backend.wait_until_indexed(user_id, document_id, self.settings.index_timeout_s)
        except BackendError as e:
            await ticker.stop()
            await status_msg.edit_text(f"⚠️ {e}")
            return
        self.last_document[message.from_user.id] = document_id

        ticker.stage = "Сверяю каждую ссылку с законами РК"
        summary_msg = status_msg
        live = LiveAnswer(summary_msg)
        events = self.backend.stream(user_id, ANALYSIS_PROMPT, document_id, self.settings.analysis_timeout_s)
        consume = asyncio.create_task(live.consume(events))
        render = asyncio.create_task(live.render())
        review = await self.backend.review(user_id, document_id, self.settings.analysis_timeout_s)
        audit = review.get("markdown") if review and review.get("problems") else None
        await ticker.stop()
        if audit:
            await safe_edit_or_send(status_msg, audit)
            summary_msg = await message.answer("✍️ Пишу краткий разбор…")
            live.messages[0] = summary_msg
        else:
            await status_msg.edit_text("✍️ Пишу разбор…")
        live.show.set()
        await asyncio.gather(consume, render)
        self.history[message.from_user.id] = deque(maxlen=HISTORY_ENTRIES)
        self._remember(message.from_user.id, f"Проверь документ «{doc.file_name}»",
                       ((audit or "") + "\n\n" + live.buffer).strip())
        if review and review.get("proposals"):
            await self._send_fixed(message, user_id, document_id, review["proposals"])
        await message.answer("Можете задать вопрос по этому документу. /new — начать с другим.")

    async def _send_fixed(self, message: Message, user_id: str, document_id: str, proposals: list[dict]) -> None:
        status = await message.answer("🛠 Вношу исправления в документ…")
        applied = await self.backend.apply_edits(user_id, document_id, proposals, self.settings.analysis_timeout_s)
        content = await self.backend.download(user_id, applied["documentId"]) if applied else None
        if not content:
            await status.edit_text("⚠️ Не удалось подготовить исправленный документ.")
            return
        done = set(applied.get("applied") or [])
        lines = [f"• п. {p.get('clause')}: «{p.get('original')}» → «{p.get('replacement')}»"
                 for p in proposals if p.get("id") in done]
        await status.delete()
        await message.answer_document(
            BufferedInputFile(content, filename=applied.get("fileName") or "исправлено.docx"),
            caption=fixed_caption(len(done)), parse_mode=None)
        await self._send_plain(message, "Внесённые исправления:\n" + "\n".join(lines))

    async def text(self, message: Message, bot: Bot) -> None:
        if message.text.startswith("/"):
            await message.answer("Не знаю такой команды. /help — что я умею.")
            return
        user_id = user_id_for(message.from_user.id)
        document_id = self.last_document.get(message.from_user.id)
        status_msg = await message.answer("⏳ Думаю…")
        ticker = StatusTicker(status_msg, "Думаю" if not document_id else "Ищу ответ в документе").start()
        live = LiveAnswer(status_msg, status=ticker)
        live.show.set()
        history = list(self.history.get(message.from_user.id, ()))
        events = self.backend.stream(user_id, message.text, document_id, self.settings.analysis_timeout_s, history)
        await asyncio.gather(live.consume(events), live.render())
        self._remember(message.from_user.id, message.text, live.buffer)

    def _remember(self, telegram_id: int, question: str, answer: str) -> None:
        entries = self.history.setdefault(telegram_id, deque(maxlen=HISTORY_ENTRIES))
        entries.append({"role": "USER", "content": question[:HISTORY_CHARS]})
        if answer:
            entries.append({"role": "ASSISTANT", "content": answer[:HISTORY_CHARS]})

    async def _send_plain(self, message: Message, text: str) -> None:
        for chunk in split_message(text):
            await message.answer(chunk, parse_mode=None)

    async def _send_long(self, message: Message, markdown: str) -> None:
        if not markdown.strip():
            await message.answer("Пустой ответ от сервиса анализа. Попробуйте ещё раз.")
            return
        for chunk in split_message(to_telegram_html(markdown)):
            try:
                await message.answer(chunk)
            except TelegramBadRequest:
                await message.answer(chunk, parse_mode=None)


async def run() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    settings = Settings.from_env()
    backend = Backend(settings.document_service_url, settings.brain_url)
    app = BotApp(settings, backend)
    bot = Bot(settings.token, default=DefaultBotProperties(parse_mode=ParseMode.HTML))
    dp = Dispatcher()
    dp.include_router(app.router)
    log.info("Bot started (docs=%s, brain=%s)", settings.document_service_url, settings.brain_url)
    try:
        await dp.start_polling(bot, handle_as_tasks=True)
    finally:
        await backend.close()
        await bot.session.close()


if __name__ == "__main__":
    asyncio.run(run())

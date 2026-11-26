from types import SimpleNamespace

from bot.main import BotApp, fixed_caption


class FakeMessage:
    def __init__(self):
        self.sent, self.documents = [], []

    async def answer(self, text, parse_mode=None):
        self.sent.append(text)
        return FakeStatus(self)

    async def answer_document(self, document, caption=None, parse_mode=None):
        self.documents.append((document.filename, document.data, caption))


class FakeStatus:
    def __init__(self, owner):
        self.owner, self.deleted, self.edited = owner, False, None

    async def delete(self):
        self.deleted = True

    async def edit_text(self, text, parse_mode=None):
        self.owner.sent.append(text)


class FakeBackend:
    def __init__(self, applied):
        self.applied = applied

    async def apply_edits(self, user_id, document_id, proposals, timeout_s):
        return self.applied

    async def download(self, user_id, document_id):
        return b"PKdocx" if self.applied else None


def app(applied):
    a = BotApp.__new__(BotApp)
    a.settings = SimpleNamespace(analysis_timeout_s=5)
    a.backend = FakeBackend(applied)
    return a


PROPOSALS = [{"id": "a", "clause": "3.1", "original": "статьёй 49", "replacement": "статьёй 36"},
             {"id": "b", "clause": "6.1", "original": "21", "replacement": "24"}]


async def test_sends_fixed_document_with_list_of_changes():
    msg = FakeMessage()
    await app({"documentId": "c1", "fileName": "Договор (исправлено).docx", "applied": ["a"]})._send_fixed(
        msg, "tg-1", "d1", PROPOSALS)
    [(name, data, caption)] = msg.documents
    assert name == "Договор (исправлено).docx" and data == b"PKdocx" and caption == fixed_caption(1)
    changes = msg.sent[-1]
    assert "п. 3.1: «статьёй 49» → «статьёй 36»" in changes and "6.1" not in changes


async def test_reports_failure_when_copy_not_created():
    msg = FakeMessage()
    await app(None)._send_fixed(msg, "tg-1", "d1", PROPOSALS)
    assert msg.documents == [] and "Не удалось" in msg.sent[-1]

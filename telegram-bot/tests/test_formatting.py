from bot.formatting import split_message, to_telegram_html


def test_converts_brain_markdown_to_telegram_html():
    md = (
        "## Риски\n\n"
        "- «В соответствии со статьёй 49» — **ошибочная ссылка**: ст. 36 ТК РК\n"
        "> Статьи не найдены: <script>\n"
        "---\n"
        "_Уровень: глубокий_"
    )
    out = to_telegram_html(md)
    assert "<b>Риски</b>" in out
    assert "• «В соответствии со статьёй 49» — <b>ошибочная ссылка</b>: ст. 36 ТК РК" in out
    assert "<blockquote>Статьи не найдены: &lt;script&gt;</blockquote>" in out
    assert "<i>Уровень: глубокий</i>" in out
    assert "---" not in out


def test_underscores_inside_words_are_not_italic():
    assert to_telegram_html("file_name_v2.txt") == "file_name_v2.txt"


def test_split_keeps_lines_whole_and_under_limit():
    text = "\n".join(f"<b>строка {i}</b> " + "x" * 50 for i in range(200))
    parts = split_message(text, limit=1000)
    assert len(parts) > 1
    assert all(len(p) <= 1000 for p in parts)
    assert all(p.count("<b>") == p.count("</b>") for p in parts)
    assert "\n".join(parts).replace("\n", "") == text.replace("\n", "")


def test_split_cuts_pathological_line():
    parts = split_message("y" * 2500, limit=1000)
    assert [len(p) for p in parts] == [1000, 1000, 500]

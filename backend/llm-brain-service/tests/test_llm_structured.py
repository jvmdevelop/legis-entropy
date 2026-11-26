from pydantic import BaseModel, RootModel

from app.llm.structured import StructuredOutput


class Items(RootModel[list[str]]):
    pass


class Obj(BaseModel):
    label: str


def fake(*answers):
    it = iter(answers)

    async def complete(system, user):
        return next(it)
    return complete


async def test_array_schema_echo_is_unwrapped():
    s = StructuredOutput(fake('{"items": ["a", "b"], "title": "Items", "type": "array"}'))
    assert (await s("sys", "u", Items)).root == ["a", "b"]


async def test_fenced_array_and_object():
    assert (await StructuredOutput(fake('```json\n["x"]\n```'))("s", "u", Items)).root == ["x"]
    assert (await StructuredOutput(fake('Вот: {"label": "ok"}'))("s", "u", Obj)).label == "ok"


async def test_repair_retry_then_none():
    s = StructuredOutput(fake("не json", '["ok"]'))
    assert (await s("s", "u", Items)).root == ["ok"]
    assert await StructuredOutput(fake("x", "y"))("s", "u", Obj) is None

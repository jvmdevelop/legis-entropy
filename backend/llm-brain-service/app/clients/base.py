import logging
from typing import Any, Callable

import httpx

from app import discovery

Resolver = Callable[[str, str | None], str]

log = logging.getLogger(__name__)


class ServiceError(RuntimeError):
    def __init__(self, service: str, status: int | None, message: str):
        super().__init__(f"{service}: {status} {message}")
        self.service = service
        self.status = status


class ServiceClient:
    def __init__(self, service: str, http: httpx.AsyncClient, fallback_url: str | None = None,
                 resolver: Resolver = discovery.resolve):
        self.service = service
        self._http = http
        self._fallback = fallback_url or f"http://{service}"
        self._resolve = resolver

    def _base(self) -> str:
        try:
            return self._resolve(self.service, self._fallback)
        except LookupError:
            return self._fallback

    async def request(self, method: str, path: str, *, params: dict | None = None, json: Any = None,
                      headers: dict | None = None, files: dict | None = None) -> Any:
        params = {k: v for k, v in (params or {}).items() if v is not None}
        headers = {k: v for k, v in (headers or {}).items() if v is not None}
        url = self._base() + path
        try:
            r = await self._http.request(method, url, params=params, json=json, headers=headers, files=files)
        except httpx.HTTPError as e:
            raise ServiceError(self.service, None, str(e)) from e
        if r.status_code >= 400:
            raise ServiceError(self.service, r.status_code, r.text[:300])
        if not r.content:
            return None
        try:
            return r.json()
        except ValueError:
            return r.text

    async def raw(self, method: str, path: str, *, headers: dict | None = None) -> httpx.Response:
        headers = {k: v for k, v in (headers or {}).items() if v is not None}
        try:
            r = await self._http.request(method, self._base() + path, headers=headers)
        except httpx.HTTPError as e:
            raise ServiceError(self.service, None, str(e)) from e
        if r.status_code >= 400:
            raise ServiceError(self.service, r.status_code, r.text[:300])
        return r

    async def get(self, path: str, **kw) -> Any:
        return await self.request("GET", path, **kw)

    async def post(self, path: str, **kw) -> Any:
        return await self.request("POST", path, **kw)

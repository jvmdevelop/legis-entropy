import logging
import socket

import py_eureka_client.eureka_client as eureka_client

from app.config import Settings

log = logging.getLogger(__name__)


async def register(settings: Settings) -> None:
    if not settings.eureka_enabled:
        return
    host = settings.instance_host or socket.gethostname()
    try:
        await eureka_client.init_async(
            eureka_server=settings.eureka_url,
            app_name=settings.app_name,
            instance_host=host,
            instance_port=settings.port,
            health_check_url=f"http://{host}:{settings.port}/actuator/health",
            status_page_url=f"http://{host}:{settings.port}/actuator/health",
        )
        log.info("Eureka client started for %s (%s:%s); registration retries until server is up",
                 settings.app_name, host, settings.port)
    except Exception as e:
        log.warning("Eureka registration failed: %s", e)


async def unregister(settings: Settings) -> None:
    if not settings.eureka_enabled:
        return
    try:
        await eureka_client.stop_async()
    except Exception as e:
        log.debug("Eureka stop failed: %s", e)


def resolve(app_name: str, fallback: str | None = None) -> str:
    client = eureka_client.get_client()
    if client is not None:
        try:
            app = client.applications.get_application(app_name.upper())
            for inst in app.up_instances:
                return f"http://{inst.hostName}:{inst.port.port}"
        except Exception as e:
            log.debug("Eureka lookup %s failed: %s", app_name, e)
    if fallback:
        return fallback.rstrip("/")
    raise LookupError(f"service {app_name} not found in Eureka")

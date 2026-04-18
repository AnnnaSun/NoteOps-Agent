const APP_SHELL_CACHE = "noteops-app-shell-v1";
const DATA_CACHE = "noteops-data-v1";
const APP_SHELL_ASSETS = [
  "/",
  "/index.html",
  "/manifest.webmanifest",
  "/icons/icon-192.svg",
  "/icons/icon-512.svg"
];

const DATA_PREFIXES = [
  "/api/v1/reviews/today",
  "/api/v1/reviews/",
  "/api/v1/tasks/today",
  "/api/v1/notes",
  "/api/v1/workspace/today",
  "/api/v1/workspace/upcoming"
];

self.addEventListener("install", (event) => {
  event.waitUntil(
    caches.open(APP_SHELL_CACHE)
      .then((cache) => cache.addAll(APP_SHELL_ASSETS))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) => Promise.all(
      keys
        .filter((key) => key !== APP_SHELL_CACHE && key !== DATA_CACHE)
        .map((key) => caches.delete(key))
    )).then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (event) => {
  const request = event.request;
  if (request.method !== "GET") {
    return;
  }

  const url = new URL(request.url);
  if (url.origin !== self.location.origin) {
    return;
  }

  if (isDataRequest(url)) {
    event.respondWith(networkFirst(request, DATA_CACHE));
    return;
  }

  if (request.mode === "navigate") {
    event.respondWith(networkFirstNavigation(request));
    return;
  }

  if (isStaticAssetRequest(request, url)) {
    event.respondWith(cacheFirst(request, APP_SHELL_CACHE));
  }
});

function isDataRequest(url) {
  if (!url.pathname.startsWith("/api/")) {
    return false;
  }
  return DATA_PREFIXES.some((prefix) => url.pathname.startsWith(prefix));
}

function isStaticAssetRequest(request, url) {
  if (APP_SHELL_ASSETS.includes(url.pathname)) {
    return true;
  }
  return ["script", "style", "image", "font"].includes(request.destination);
}

async function cacheFirst(request, cacheName) {
  const cache = await caches.open(cacheName);
  const cached = await cache.match(request);
  if (cached) {
    return cached;
  }
  const response = await fetch(request);
  if (response && response.ok) {
    cache.put(request, response.clone());
  }
  return response;
}

async function networkFirst(request, cacheName) {
  const cache = await caches.open(cacheName);
  try {
    const response = await fetch(request);
    if (response && response.ok) {
      cache.put(request, response.clone());
    }
    return response;
  } catch (error) {
    const cached = await cache.match(request);
    if (cached) {
      return cached;
    }
    throw error;
  }
}

async function networkFirstNavigation(request) {
  const cache = await caches.open(APP_SHELL_CACHE);
  try {
    const response = await fetch(request);
    if (response && response.ok) {
      cache.put("/index.html", response.clone());
    }
    return response;
  } catch (error) {
    const cached = await cache.match("/index.html");
    if (cached) {
      return cached;
    }
    throw error;
  }
}

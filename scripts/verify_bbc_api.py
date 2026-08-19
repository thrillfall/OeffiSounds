#!/usr/bin/env python3
"""
Verify the BBC Sounds API integration end-to-end.

Mirrors the exact logic from:
  - BBCSoundsPodcastSearcher.java  (search → RSS feed URL)
  - AntennapodHttpClient.java      (BBC URL upgrade interceptor, User-Agent)

Exits 0 if all checks pass, 1 otherwise.
"""

import argparse
import json
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

BBC_SEARCH_URL = "https://rms.api.bbc.co.uk/v2/experience/inline/search?q={query}&limit=24"
BBC_CONTAINER_SEARCH_URL = "https://rms.api.bbc.co.uk/v2/programmes/search/container?q={query}"
BBC_FEED_TEMPLATE = "https://podcasts.files.bbci.co.uk/{pid}.rss"
BBC_HOSTS = (".bbc.co.uk", ".bbci.co.uk")
DEFAULT_QUERIES = ["Desert Island Discs", "In Our Time"]
REQUEST_TIMEOUT = 15
USER_AGENT = "AntennaPod/0.0.0"


def upgrade_bbc_url(url):
    """Mirror the AntennapodHttpClient BBC URL upgrade interceptor."""
    if url.startswith("http://"):
        parsed = urllib.parse.urlparse(url)
        if any(parsed.hostname.endswith(h) for h in BBC_HOSTS):
            url = url.replace("http://", "https://", 1)
            url = url.replace("/proto/http/", "/proto/https/")
    return url


def _make_request(url, method="GET", headers=None):
    req = urllib.request.Request(url, method=method)
    req.add_header("User-Agent", USER_AGENT)
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)
    return urllib.request.urlopen(req, timeout=REQUEST_TIMEOUT)


def search_bbc(query):
    """Mirror BBCSoundsPodcastSearcher.search()."""
    encoded = urllib.parse.quote(query, safe="")
    url = BBC_SEARCH_URL.format(query=encoded)
    print(f"  GET {url}")

    with _make_request(url) as resp:
        body = resp.read().decode("utf-8")

    result = json.loads(body)
    modules = result.get("data", [])

    podcasts = []
    for module in modules:
        if module.get("id") != "container_search":
            continue
        items = module.get("data")
        if items is None:
            break
        for item in items:
            pid = item.get("id") or ""
            if not pid:
                continue
            feed_url = BBC_FEED_TEMPLATE.format(pid=pid)
            titles = item.get("titles") or {}
            title = titles.get("primary", "")
            network = item.get("network") or {}
            author = network.get("short_title") or None
            image_url = item.get("image_url") or None
            if image_url:
                image_url = image_url.replace("{recipe}", "512x512")
            podcasts.append({
                "pid": pid,
                "title": title,
                "author": author,
                "image_url": image_url,
                "feed_url": feed_url,
            })
        break  # matches Java `break` after first container_search module

    return podcasts


def search_bbc_container(query):
    """Alternative: dedicated container search endpoint (not module-walk required)."""
    encoded = urllib.parse.quote(query, safe="")
    url = BBC_CONTAINER_SEARCH_URL.format(query=encoded)
    print(f"  GET {url}")

    with _make_request(url) as resp:
        body = resp.read().decode("utf-8")

    result = json.loads(body)
    items = result.get("data", [])

    podcasts = []
    for item in items:
        pid = item.get("id") or ""
        if not pid:
            continue
        feed_url = BBC_FEED_TEMPLATE.format(pid=pid)
        titles = item.get("titles") or {}
        title = titles.get("primary", "")
        network = item.get("network") or {}
        author = network.get("short_title") or None
        image_url = item.get("image_url") or None
        if image_url:
            image_url = image_url.replace("{recipe}", "512x512")
        podcasts.append({
            "pid": pid,
            "title": title,
            "author": author,
            "image_url": image_url,
            "feed_url": feed_url,
        })

    return podcasts


def compare_search_endpoints(query):
    """Run both search endpoints for a query and print a side-by-side comparison."""
    print("=" * 60)
    print(f"Comparing search endpoints for: '{query}'")
    print()

    print("  [inline/search + container_search module]")
    try:
        inline_results = search_bbc(query)
        print(f"  -> {len(inline_results)} result(s)")
    except Exception as ex:
        inline_results = []
        print(f"  -> FAILED: {ex}")

    print()
    print("  [programmes/search/container]")
    try:
        container_results = search_bbc_container(query)
        print(f"  -> {len(container_results)} result(s)")
    except Exception as ex:
        container_results = []
        print(f"  -> FAILED: {ex}")

    print()

    inline_pids  = [r["pid"] for r in inline_results]
    container_pids = [r["pid"] for r in container_results]

    # Ordering
    if inline_pids == container_pids:
        print("  ORDER    identical")
    else:
        print("  ORDER    differ")

    # Coverage
    only_inline    = [p for p in inline_pids    if p not in container_pids]
    only_container = [p for p in container_pids if p not in inline_pids]
    if only_inline:
        titles = {r["pid"]: r["title"] for r in inline_results}
        print(f"  ONLY inline:     {[titles[p] for p in only_inline]}")
    if only_container:
        titles = {r["pid"]: r["title"] for r in container_results}
        print(f"  ONLY container:  {[titles[p] for p in only_container]}")
    if not only_inline and not only_container:
        print("  RESULTS  same PIDs returned by both")

    # Field presence check on first result
    for label, results in [("inline", inline_results), ("container", container_results)]:
        if not results:
            continue
        r = results[0]
        missing = [f for f in ("pid", "title", "author", "image_url") if not r.get(f)]
        if missing:
            print(f"  FIELDS   {label}: missing {missing} on first result")
        else:
            print(f"  FIELDS   {label}: all fields present on first result")

    print()
    print("  Results (inline):")
    for r in inline_results:
        print(f"    {r['pid']}  {r['title']!r:40s}  {r['author']}")
    print()
    print("  Results (container):")
    for r in container_results:
        print(f"    {r['pid']}  {r['title']!r:40s}  {r['author']}")


def fetch_rss(feed_url):
    """GET the RSS feed, return raw bytes."""
    print(f"  GET {feed_url}")
    with _make_request(feed_url) as resp:
        return resp.read()


def parse_rss_episodes(xml_bytes):
    """Return media URLs from <enclosure> elements, with BBC URL upgrade applied."""
    root = ET.fromstring(xml_bytes)
    urls = []
    for item in root.iter("item"):
        enc = item.find("enclosure")
        if enc is not None:
            url = enc.get("url", "")
            if url:
                urls.append(upgrade_bbc_url(url))
    return urls


def verify_media_url(url):
    """HEAD (or ranged GET) the media URL. Returns (success, reason)."""
    try:
        with _make_request(url, method="HEAD") as resp:
            code = resp.status
            if 200 <= code < 300:
                return True, f"HTTP {code}"
            return False, f"HTTP {code}"
    except urllib.error.HTTPError as e:
        if e.code == 405:
            # Some CDNs reject HEAD — fall back to a tiny ranged GET
            try:
                with _make_request(url, headers={"Range": "bytes=0-0"}) as resp:
                    code = resp.status
                    if 200 <= code < 300 or code == 206:
                        return True, f"HTTP {code} (via GET Range)"
                    return False, f"HTTP {code} (via GET Range)"
            except urllib.error.HTTPError as e2:
                return False, f"HTTP {e2.code} (via GET Range)"
            except Exception as ex:
                return False, str(ex)
        return False, f"HTTP {e.code}"
    except Exception as ex:
        return False, str(ex)


def run_check(query, dry_run):
    """Run one full end-to-end check. Returns True on PASS."""
    print("=" * 60)
    print(f"Query: '{query}'")

    # Step 1: search
    try:
        results = search_bbc(query)
    except Exception as ex:
        print(f"  FAIL  Search request failed: {ex}")
        print(f"  FAIL  '{query}'")
        return False

    if not results:
        print("  FAIL  Search returned no results")
        print(f"  FAIL  '{query}'")
        return False

    print(f"  OK    Search returned {len(results)} result(s)")

    first = results[0]
    print(f"  OK    First result: '{first['title']}'  "
          f"(PID: {first['pid']}, author: {first['author']})")
    print(f"        Feed URL: {first['feed_url']}")

    # Step 2: fetch RSS
    try:
        xml_bytes = fetch_rss(first["feed_url"])
    except Exception as ex:
        print(f"  FAIL  RSS fetch failed: {ex}")
        print(f"  FAIL  '{query}'")
        return False

    print(f"  OK    RSS fetched ({len(xml_bytes)} bytes)")

    # Step 3: parse enclosures
    try:
        media_urls = parse_rss_episodes(xml_bytes)
    except Exception as ex:
        print(f"  FAIL  RSS parse error: {ex}")
        print(f"  FAIL  '{query}'")
        return False

    if not media_urls:
        print("  FAIL  No enclosures found in RSS feed")
        print(f"  FAIL  '{query}'")
        return False

    print(f"  OK    Found {len(media_urls)} episode(s) with enclosures")
    print(f"        First media URL: {media_urls[0]}")

    # Step 4: verify media URL (unless --dry-run)
    if dry_run:
        print("        (skipping media URL check: --dry-run)")
    else:
        print(f"  HEAD  {media_urls[0]}")
        ok, reason = verify_media_url(media_urls[0])
        if not ok:
            print(f"  FAIL  Media URL not accessible: {reason}")
            print(f"  FAIL  '{query}'")
            return False
        print(f"  OK    Media URL accessible ({reason})")

    print(f"  PASS  '{query}'")
    return True


def main():
    parser = argparse.ArgumentParser(
        description="Verify BBC Sounds API integration end-to-end."
    )
    parser.add_argument(
        "queries",
        nargs="*",
        default=DEFAULT_QUERIES,
        metavar="QUERY",
        help="Search queries to test (default: %(default)s)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Skip the final media URL HEAD check (faster)",
    )
    parser.add_argument(
        "--compare-search",
        action="store_true",
        help="Compare inline/search vs programmes/search/container and exit",
    )
    args = parser.parse_args()

    if args.compare_search:
        for q in args.queries:
            compare_search_endpoints(q)
        print("=" * 60)
        sys.exit(0)

    results = [run_check(q, args.dry_run) for q in args.queries]
    passed = sum(results)
    total = len(results)

    print("=" * 60)
    print(f"Summary: {passed}/{total} checks passed")

    sys.exit(0 if passed == total else 1)


if __name__ == "__main__":
    main()

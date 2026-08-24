import { fileURLToPath } from 'url';

// Standard ranking lists compiled from the moviebox endpoints
export const RANKING_LISTS = {
  TRENDING_NOW: "872031290915189720",
  TRENDING_MOVIE: "8821254238245470240",
  TRENDING_DRAMA: "8617025562613270856",
  TRENDING_ANIME: "567783349092340776",
  INDO_FILM: "6528093688173053896",
  K_DRAMA: "4380734070238626200",
  INDO_DRAMA: "5283462032510044280",
  ANIME: "8617025562613270856",
  HOLLYWOOD: "1469286917119311888",
  C_DRAMA: "8624142774394406504",
  INDO_HORROR: "5848753831881965888",
  THAI_DRAMA: "1164329479448281992",
  SHORT_TV: "567783349092340776",
  FUNNY_HORROR_CRIME: "3528002473103362040",
  INDO_DUBBED: "5549742004948601072",
  RECENTLY_ADDED: "4019055174353407000",
  INDONESIAN_KILLERS: "5863917898430924656",
  HAPPY_LIFE: "4993310637209048808",
  RUN_ESCAPE_DEATH: "8703838933408530536",
  BAD_ROMANCE: "4539350473970797944",
  CYBERPUNK: "3766111568753312664",
  MONSTER_TITAN: "1653005382303864120",
  SEA_ADVENTURE: "6708972608207443352"
};

export default class MovieboxScraper {
  constructor(options = {}) {
    this.baseUrl = options.baseUrl || 'https://h5-api.aoneroom.com';
    this.playHost = options.playHost || 'https://themoviebox.xyz';
    this.token = options.token || null;
    this.userAgent = options.userAgent || 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36';
  }

  /**
   * Initializes or refreshes the JWT token by calling the home endpoint
   */
  async initToken() {
    const homeUrl = `${this.baseUrl}/wefeed-h5api-bff/home?host=themoviebox.xyz`;
    const res = await fetch(homeUrl, {
      headers: { 'User-Agent': this.userAgent }
    });

    if (!res.ok) {
      throw new Error(`Failed to contact Moviebox Home API. Status: ${res.status}`);
    }

    const setCookie = res.headers.get('set-cookie');
    if (setCookie) {
      const match = setCookie.match(/token=([^;]+)/);
      if (match) {
        this.token = match[1];
        return this.token;
      }
    }

    const xUser = res.headers.get('x-user');
    if (xUser) {
      try {
        const parsed = JSON.parse(xUser);
        if (parsed.token) {
          this.token = parsed.token;
          return this.token;
        }
      } catch (e) {}
    }

    throw new Error('Failed to retrieve token from Home API response headers');
  }

  /**
   * Base request runner with automatic token initialization and retry-on-expiry logic
   */
  async request(path, options = {}) {
    if (!this.token) {
      await this.initToken();
    }

    const url = path.startsWith('http') ? path : `${this.baseUrl}${path}`;
    const headers = {
      'Content-Type': 'application/json',
      'User-Agent': this.userAgent,
      'Authorization': `Bearer ${this.token}`,
      ...options.headers
    };

    const fetchOptions = {
      ...options,
      method: options.method || 'GET',
      headers
    };

    let res = await fetch(url, fetchOptions);
    let data;

    try {
      data = await res.json();
    } catch (e) {
      const text = await res.text();
      throw new Error(`Invalid JSON response: ${text.substring(0, 200)}`);
    }

    // Auto re-authenticate once if token expires
    if (data && (data.code === 400 || data.message === 'invalid token')) {
      console.warn('[Scraper] Token expired/invalid. Re-initializing token...');
      await this.initToken();

      headers['Authorization'] = `Bearer ${this.token}`;
      res = await fetch(url, fetchOptions);
      data = await res.json();
    }

    return data;
  }

  async get(path, searchParams = {}, headers = {}) {
    const query = new URLSearchParams(searchParams).toString();
    const fullPath = query ? `${path}?${query}` : path;
    return this.request(fullPath, { method: 'GET', headers });
  }

  async post(path, body = {}, headers = {}) {
    return this.request(path, {
      method: 'POST',
      headers,
      body: JSON.stringify(body)
    });
  }

  /**
   * Get homepage contents (banners, platforms, popular sections)
   */
  async getHome() {
    return this.get('/wefeed-h5api-bff/home', { host: 'themoviebox.xyz' });
  }

  /**
   * Get overall most trending items
   */
  async getTrending(page = 0, perPage = 18) {
    return this.get('/wefeed-h5api-bff/subject/trending', { page, perPage });
  }

  /**
   * Get most trending movies specifically
   */
  async getTrendingMovies(page = 0, perPage = 18) {
    return this.get('/wefeed-h5api-bff/subject/trending', { tabId: 'ONEROOM_MOVIE', page, perPage });
  }

  /**
   * Get details for a movie/series using its detailPath
   */
  async getDetail(detailPath) {
    return this.get('/wefeed-h5api-bff/detail', { detailPath });
  }

  /**
   * Get detail and recommended films/series
   */
  async getDetailRec(subjectId, page = 1, perPage = 12) {
    return this.get('/wefeed-h5api-bff/subject/detail-rec', { subjectId, page, perPage });
  }

  /**
   * Search for items matching keyword
   * subjectType: 0 (All), 1 (Movies), 2 (Series), 6 (Music)
   */
  async search(keyword, page = 1, perPage = 10, subjectType = 0) {
    return this.post('/wefeed-h5api-bff/subject/search', { keyword, page, perPage, subjectType });
  }

  /**
   * Get play/streaming resources for a movie/episode
   * se (Season): 0 for movies, 1+ for series
   * ep (Episode): 0 for movies, 1+ for series
   */
  async getStream(subjectId, detailPath, se = 0, ep = 0) {
    // Extract slug to generate referer header
    const slug = detailPath.split('/').filter(Boolean).pop() || detailPath;
    const referer = `${this.playHost}/movies/${slug}`;
    const playUrl = `${this.playHost}/wefeed-h5api-bff/subject/play`;

    return this.get(playUrl, {
      subjectId,
      se,
      ep,
      detailPath,
      streamSignType: 1
    }, {
      'Referer': referer
    });
  }

  /**
   * Filter TV Shows
   */
  async filterTvShow({ page = 1, perPage = 28, country = 'All', sort = 'ForYou', rate = ['0', '10'], classify = 'All' } = {}) {
    return this.post('/wefeed-h5api-bff/subject/filter', {
      page,
      perPage,
      channelId: 2,
      country,
      sort,
      rate,
      classify
    });
  }

  /**
   * Filter Animations
   */
  async filterAnimation({ page = 1, perPage = 28, country = 'All', year = 'All', sort = 'ForYou' } = {}) {
    return this.post('/wefeed-h5api-bff/subject/filter', {
      page,
      perPage,
      channelId: 1006,
      country,
      year,
      sort
    });
  }

  /**
   * Filter Movies
   */
  async filterMovie({ page = 1, perPage = 28, genre = 'All', country = 'All', year = 'All', sort = 'ForYou', classify = 'All' } = {}) {
    return this.post('/wefeed-h5api-bff/subject/filter', {
      page,
      perPage,
      channelId: 1,
      genre,
      country,
      year,
      sort,
      classify
    });
  }

  /**
   * Fetch movies/drama lists by ranking list ID
   */
  async getRankingList(rankingListId, page = 1, perPage = 12) {
    return this.get('/wefeed-h5api-bff/ranking-list/content', { id: rankingListId, page, perPage });
  }

  /**
   * Fetch the list of movies tabs
   */
  async getMovieTab() {
    return this.get('/wefeed-h5api-bff/tab-operating', { tabId: 'ONEROOM_MOVIE', host: 'themoviebox.xyz' });
  }
}

// ==========================================
// CLI Direct Execution Block
// ==========================================
if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const args = process.argv.slice(2);
  const action = args[0];

  if (!action || action === '--help' || action === '-h') {
    console.log(`
Moviebox Scraper CLI
Usage: node moviebox_scraper.js <action> [options]

Actions:
  home                              Get homepage items
  trending [movies|all]             Get trending items
  search <keyword> [page]           Search items
  detail <detailPath>               Get details by path
  stream <subjectId> <detailPath> [se] [ep] Get play stream details
  ranking <rankingNameOrId>         Get ranking content
  filter-movie [genre] [country]    Filter movies (default: All, All)
  filter-tv [country] [sort]        Filter TV shows (default: All, ForYou)

Examples:
  node moviebox_scraper.js search Avatar
  node moviebox_scraper.js detail haba-baba-doo-puti-puti-poo-84sJSeUbWg8
  node moviebox_scraper.js stream 6943693970019338024 haba-baba-doo-puti-puti-poo-84sJSeUbWg8 0 0
  node moviebox_scraper.js ranking TRENDING_NOW
    `);
    process.exit(0);
  }

  const scraper = new MovieboxScraper();

  try {
    let result;
    switch (action) {
      case 'home':
        result = await scraper.getHome();
        break;
      case 'trending':
        const type = args[1] || 'all';
        result = type === 'movies' ? await scraper.getTrendingMovies() : await scraper.getTrending();
        break;
      case 'search':
        if (!args[1]) throw new Error('Missing search keyword. Usage: search <keyword>');
        result = await scraper.search(args[1], parseInt(args[2] || '1', 10));
        break;
      case 'detail':
        if (!args[1]) throw new Error('Missing detail path. Usage: detail <detailPath>');
        result = await scraper.getDetail(args[1]);
        break;
      case 'stream':
        if (!args[1] || !args[2]) throw new Error('Missing arguments. Usage: stream <subjectId> <detailPath> [se] [ep]');
        result = await scraper.getStream(args[1], args[2], parseInt(args[3] || '0', 10), parseInt(args[4] || '0', 10));
        break;
      case 'ranking':
        if (!args[1]) throw new Error('Missing ranking ID/name. Usage: ranking <nameOrId>');
        const id = RANKING_LISTS[args[1]] || args[1];
        result = await scraper.getRankingList(id);
        break;
      case 'filter-movie':
        result = await scraper.filterMovie({ genre: args[1] || 'All', country: args[2] || 'All' });
        break;
      case 'filter-tv':
        result = await scraper.filterTvShow({ country: args[1] || 'All', sort: args[2] || 'ForYou' });
        break;
      default:
        throw new Error(`Unknown action: ${action}`);
    }

    console.log(JSON.stringify(result, null, 2));
  } catch (error) {
    console.error(`[CLI Error]`, error.message);
    process.exit(1);
  }
}

    
/*
import MovieboxScraper, { RANKING_LISTS } from './moviebox_scraper.js';

async function main() {
  const scraper = new MovieboxScraper();
  
  try {
    console.log('--- 1. Searching for Movies ---');
    const searchResult = await scraper.search('Avatar');
    console.log(`Found ${searchResult.data?.items?.length || 0} items.`);
    
    if (searchResult.data?.items?.length > 0) {
      const item = searchResult.data.items[0];
      console.log(`First item: "${item.title}" (ID: ${item.subjectId}, Path: ${item.detailPath})`);
      
      console.log('\n--- 2. Fetching Item Details ---');
      const details = await scraper.getDetail(item.detailPath);
      console.log(`Title: ${details.data?.subject?.title}`);
      console.log(`Description: ${details.data?.subject?.description?.substring(0, 150)}...`);
      
      console.log('\n--- 3. Fetching Stream/Play Resources ---');
      const streamInfo = await scraper.getStream(item.subjectId, item.detailPath, 0, 0);
      console.log(`Has Resource: ${streamInfo.data?.hasResource}`);
      if (streamInfo.data?.streams?.length > 0) {
        console.log('Available streams:');
        for (const stream of streamInfo.data.streams) {
          console.log(`  - ${stream.resolutions}p (${stream.format}): ${stream.url}`);
        }
      }
    }
    
    console.log('\n--- 4. Fetching Trending Movie List ---');
    const trending = await scraper.getTrendingMovies();
    console.log('Trending Raw Response Code:', trending.code, trending.message);
    console.log('Trending Items Count:', trending.data?.subjectList?.length || 0);
    
    console.log('\n--- 5. Fetching Ranking List (Trending Drama) ---');
    const ranking = await scraper.getRankingList(RANKING_LISTS.TRENDING_DRAMA);
    console.log('Ranking Raw Response Code:', ranking.code, ranking.message);
    console.log('Ranking Items Count:', ranking.data?.subjectList?.length || 0);
    
  } catch (error) {
    console.error('Error during execution:', error);
  }
}

main();
*/
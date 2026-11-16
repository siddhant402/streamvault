import type { NextConfig } from 'next';

// The web app forwards /api/* to the server, so the browser only ever talks to one address.
// That keeps the session cookie same-origin, needs no CORS, and lets phones and TVs on the
// WiFi use http://<this-PC>:3000 without knowing the server's port.
const BACKEND = process.env.SV_BACKEND_URL ?? 'http://127.0.0.1:8080';

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${BACKEND}/api/:path*` }];
  },
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: 'image.tmdb.org',
        pathname: '/t/p/**',
      },
    ],
  },
};

export default nextConfig;

import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Disable StrictMode in dev — it double-invokes effects causing duplicate API calls.
  // Re-enable before production: set to true or remove this line.
  reactStrictMode: false,
};

export default nextConfig;

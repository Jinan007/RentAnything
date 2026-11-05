import { existsSync } from "node:fs";
import { Config } from "@remotion/cli/config";

Config.setVideoImageFormat("jpeg");
Config.setOverwriteOutput(true);

// The sandbox cannot download Remotion's bundled Chrome, so use the preinstalled one when present.
// On your own machine this path won't exist and Remotion downloads its browser automatically.
const preinstalledChrome = "/opt/pw-browsers/chromium";
if (existsSync(preinstalledChrome)) {
  Config.setBrowserExecutable(preinstalledChrome);
}

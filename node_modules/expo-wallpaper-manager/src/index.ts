// Import the native module. On web, it will be resolved to ExpoWallpaperManager.web.ts
// and on native platforms to ExpoWallpaperManager.ts
import ExpoWallpaperManagerModule from "./ExpoWallpaperManagerModule";

export function hello(): string {
  return ExpoWallpaperManagerModule.hello();
}

export function setWallpaper(options): string {
  return ExpoWallpaperManagerModule.setWallpaper(options);
}

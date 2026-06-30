# Schworlium (the highly anticipated sequel to Worlium)

Schworlium is a Worley-noise cave generation mod for Minecraft 26.2.x and 1.21.11 targeting both Fabric and NeoForge. Right now, It's essentially an opinionated port of SuperFluke's Worley's Caves, but it will eventually diverge into a more fleshed-out spelunking mod.

As expected, the carver replaces vanilla's caves with hollow networks shaped by 3D Worley noise.
  
The two substantive changes that pulled the project away from being a 1:1 port are a smoother carve and a taller working range. The original implementation contained jittery per-block math in its noise sampling and threshold logic that produced visibly clunky walls and unnatural seams between regions. This has been replaced with the smoothing approach used in Worlium, yielding cleaner surfaces and more coherent cave shapes. Separately, the carver has been adapted to the post-1.18 extended world height. Due to this change, certain configuration options that no longer made sense were dropped at the same time.


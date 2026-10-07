# Changelog

## [Unreleased]

### Changed

- Rename the mod to Bedrock Block Placement (mod id `bedrockblockplacement`), author Niceninjapro
- Set the default `defaultBreakingSpeedRatio` to 2.0
- New logo and banner

### Performance

- Compute the player movement once per tick instead of twice, and avoid a temporary vector allocation while locked to a breaking line

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Rejects²** is an updated, modern continuation of the original [Meteor Rejects](https://github.com/AntiCope/meteor-rejects) addon for Meteor Client. This repository represents the completed port to Minecraft 1.21.10, providing a clean public-facing codebase with a fresh commit history.

This addon extends Meteor Client with modules and commands that were either:
- Removed from Meteor Client in previous updates
- Ported from other Minecraft clients (Wurst, BleachHack, LiquidBounce, etc.)
- Contributed through unmerged pull requests
- Custom implementations for specialized use cases

**Current Status**: Base porting work to Minecraft 1.21.10 and Meteor Client 1.21.10-32 is complete. All modules, commands, and features are functional.

## Build Commands

**IMPORTANT**: Always use the `gradle-mcp-server` MCP tools for Gradle interactions. Do NOT use terminal commands like `./gradlew build` directly.

```bash
# Use gradle_build tool from gradle-mcp-server
# Use gradle_execute tool for custom tasks
```

The built JAR will be in `build/libs/meteor-rejects-addon-<version>.jar`

## Development Environment

### Required Dependencies

- Java 21 or higher
- Minecraft 1.21.10
- Fabric Loader 0.17.3+
- Meteor Client 1.21.10-32 (included as local JAR in `libs/`)

### Key External Dependencies

- **Baritone** (1.21.10): Pathfinding and mining automation integration
- **Orbit** (0.2.4): Event bus system used by Meteor
- **Starscript** (0.2.5): Scripting language for Meteor
- **Cubiomes** (1.22.3): Native seed finding libraries (bundled with platform-specific JNI)
- **mc_core/mc_feature/mc_biome** (SeedFinding): Java-based seed utilities for `.locate` and OreSim
- **SeedcrackerX API** (2.10.1): Integration with SeedcrackerX for automatic seed detection

## Architecture

### Package Structure

```
anticope.rejects/
├── MeteorRejectsAddon.java        # Main addon entry point - registers all modules/commands
├── arguments/                      # Custom command argument types
├── commands/                       # Chat commands (.command prefix)
├── events/                         # Custom event definitions
├── gui/
│   ├── hud/                       # HUD elements (RadarHud)
│   ├── screens/                   # Custom GUI screens
│   ├── servers/                   # Server finder/manager screens
│   └── themes/rounded/            # Meteor Rounded theme implementation
├── mixin/                         # Mixins into vanilla Minecraft
│   └── baritone/                  # Mixins into Baritone
├── mixininterface/                # Interfaces for mixin accessor communication
├── modules/                       # Meteor modules (main features)
│   ├── bots/                      # Bot modules (ElytraBot, LawnBot, MossBot)
│   └── misc/                      # Miscellaneous modules
├── settings/                      # Custom setting types
└── utils/                         # Utility classes
    ├── accounts/                  # Yggdrasil account integration
    ├── gui/                       # GUI rendering utilities
    ├── portscanner/               # Server port scanning
    ├── seeds/                     # Seed management and utilities
    └── server/                    # Server-related utilities
```

### Addon Registration Flow

1. **MeteorRejectsAddon.onInitialize()**: Entry point called by Meteor's addon system
   - Registers all modules via `Modules.get().add()`
   - Registers all commands via `Commands.add()`
   - Registers HUD elements via `Systems.get(Hud.class).register()`
   - Registers GUI themes via `GuiThemes.add()`

2. **MeteorRejectsAddon.onRegisterCategories()**: Registers the "Rejects²" category
   - Creates custom category with barrier item icon
   - Modules are assigned to this category in their constructors

3. **Module lifecycle**: All modules extend `meteordevelopment.meteorclient.systems.modules.Module`
   - Settings registered in constructor
   - Event handlers use Orbit's `@EventHandler` annotation
   - Access Minecraft client via inherited `mc` field

### Threading Architecture

- **MODULE_THREAD**: Single-thread executor (`Executors.newSingleThreadExecutor()`) for background module tasks
- Used by modules like ElytraBot that need asynchronous processing without blocking the game thread
- Access via `MeteorRejectsAddon.MODULE_THREAD`

## Key Subsystems

### Mixin System

Two mixin configuration files:
- **meteor-rejects.mixins.json**: Mixins into vanilla Minecraft and Baritone
- **meteor-rejects-meteor.mixins.json**: Mixins into Meteor Client itself (for modifications)

Naming conventions:
- Accessor mixins: `*Accessor.java` (expose private fields/methods)
- Regular mixins: `*Mixin.java` (modify behavior)
- Mixin interfaces: Place in `mixininterface/` package for cleaner API

### Access Widener

File: `src/main/resources/meteor-rejects.accesswidener`

Used to access package-private Minecraft internals. Add entries here when legitimate access is needed to otherwise inaccessible classes or members.

### Configuration System

**RejectsConfig** (`utils/RejectsConfig.java`): Custom configuration extending Meteor's config system
- HTTP request configuration (allowed domains, user agent)
- Hidden modules list
- Font loading settings
- Duplicate module name handling

Access via settings UI or programmatically.

### SeedcrackerX Integration

Entry point: `anticope.rejects.utils.SeedCrackerEP`
- Registered via `seedcrackerx` entrypoint in `fabric.mod.json`
- Enables automatic seed detection for `.locate` command and OreSim module
- Bridges SeedcrackerX data with addon's seed utilities

## Module Development Patterns

### Basic Module Structure

```java
public class ExampleModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> exampleSetting = sgGeneral.add(
        new BoolSetting.Builder()
            .name("example")
            .description("Example setting")
            .defaultValue(true)
            .build()
    );

    public ExampleModule() {
        super(MeteorRejectsAddon.CATEGORY, "example", "Example module");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Module logic here
    }
}
```

### Common Event Handlers

- `TickEvent.Pre/Post`: Every game tick (20 times/second)
- `SendMovementPacketsEvent.Pre/Post`: Before/after movement packets sent
- `PacketEvent.Send/Receive`: Network packet interception
- `Render3DEvent`: World rendering (for ESP, tracers, etc.)
- `Render2DEvent`: HUD/overlay rendering

### Settings Best Practices

- Group related settings using `SettingGroup`
- Use descriptive names and helpful descriptions
- Provide sensible defaults
- Use `.visible()` for conditional settings visibility
- Chain `.onChanged()` for settings that need reactive behavior

## Command Development

Commands extend `meteordevelopment.meteorclient.commands.Command`:

```java
public class ExampleCommand extends Command {
    public ExampleCommand() {
        super("example", "Example command", "ex");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            info("Command executed!");
            return SINGLE_SUCCESS;
        });
    }
}
```

Register in `MeteorRejectsAddon.onInitialize()` via `Commands.add(new ExampleCommand())`.

## Common Minecraft 1.21.10 APIs

### Rendering (DrawContext)

Modern rendering uses `DrawContext` instead of `MatrixStack`:

```java
public void render(DrawContext context, int mouseX, int mouseY, float delta) {
    MatrixStack matrices = context.getMatrices();
    // Use context.draw*() methods for rendering
    // Direct RenderSystem calls still work for shaders/state
}
```

### Player Inventory

- Get selected slot: `player.getInventory().getSelectedSlot()`
- Get main hand stack: `player.getInventory().main.get(player.getInventory().getSelectedSlot())`
- Off-hand stack: `player.getOffHandStack()`

### Data Components (replaced NBT in many cases)

1.21.10 moved many NBT operations to DataComponentTypes:
- Item properties: `stack.get(DataComponentTypes.CUSTOM_NAME)`
- Setting data: `stack.set(DataComponentTypes.LORE, loreList)`

NBT still used for certain operations (world data, entity data, etc.)

## Reference Materials

### ai_reference Directory

**CRITICAL**: This directory (git-ignored) contains living reference implementations:
- Meteor Client 1.21.10 source code
- Working addon examples (Catpuccin, Trouser-Streak, Nora-Tweaks, etc.)
- Meteor Client's Baritone implementation
- Dependency sources (Starscript, Orbit)

**ALWAYS read `ai_reference/INDEX.md` first** when you need reference material. This index will guide you to the correct workspace for your specific task and save time searching.

When implementing new features or updating APIs, check `ai_reference/` for working examples before proceeding.

**Updating the reference database**: When asked to "update ai_reference" or "update the reference database", run:
```bash
cd ai_reference
python refresh_references.py
```
This script purges and re-clones all 13 reference repositories to their latest versions.

### MCP Servers

This workspace has 5 specialized MCP servers configured for Minecraft development:

1. **minecraft-registry-mcp**: Registry data, block properties, version switching, code generation
2. **minecraft-versiondiff-mcp**: Breaking changes analysis, mapping migrations, mod compatibility
3. **mixin-mcp-server**: Mixin development, injection point finding, descriptor validation
4. **gradle-mcp-server**: Gradle project management, builds, tests, dependencies
5. **linkie-mcp-server**: Obfuscation mappings (Yarn/Mojang/MCP), decompiled source, namespace translation

**Usage Guidelines**:
- **ALWAYS use `gradle-mcp-server`** for Gradle operations - never use `./gradlew` terminal commands directly
- **ALWAYS use `mixin-mcp-server` and `linkie-mcp-server`** when working with mixins, mappings, or injection points
- Use `minecraft-registry-mcp` for block states, registry lookups, and Minecraft version data
- Use `minecraft-versiondiff-mcp` for understanding API changes between versions
- These tools provide deep integration with Minecraft modding workflows

### Useful Resources

- [Meteor Client GitHub](https://github.com/MeteorDevelopment/meteor-client)
- [Fabric Wiki](https://fabricmc.net/wiki/)
- [Yarn Mappings](https://github.com/FabricMC/yarn) - Minecraft deobfuscation
- Original Meteor Rejects: https://github.com/AntiCope/meteor-rejects

## Testing

No automated test suite - testing is manual:

1. Build with `./gradlew build`
2. Copy JAR from `build/libs/` to `.minecraft/mods/`
3. Launch Minecraft 1.21.10 with Fabric and Meteor Client installed
4. Test modules/commands in-game

## Code Style

- Follow existing code conventions in the codebase
- Use Meteor Client's style as reference (consistent with addon pattern)
- Prefer descriptive variable names over short abbreviations
- Comment complex logic, especially in mixins
- Keep modules focused on single responsibility

## Important Notes

- This is the **public-facing repository** with a clean commit history
- The original porting work was done in `C:\Users\Cope\Documents\GitHub\meteor-rejects-1.21.10`
- All base migration work is complete - this repo is ready for ongoing development
- When adding new features, follow the established patterns in existing modules
- Always test changes in-game before committing

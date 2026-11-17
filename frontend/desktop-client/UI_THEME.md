# Kinetic Telemetry UI

## Design tokens

| Token | Value | Use |
| --- | --- | --- |
| `-kt-bg` | `#080A0F` | Application background |
| `-kt-surface-1` | `#10141E` | Panels and cards |
| `-kt-surface-2` | `#181F2E` | Elevated controls and navigation |
| `-kt-text` | `#F8FAFC` | Primary text |
| `-kt-muted` | `#7E8DA4` | Supporting text and hairlines |
| `-kt-accent` | `#00F0FF` | Focus, telemetry and active navigation |
| `-kt-success` | `#00E676` | Healthy state |
| `-kt-warning` | `#FFEA00` | Warning state |
| `-kt-critical` | `#FF1744` | Critical or failed state |
| `-kt-offline` | `#455A64` | Disconnected sensors |

The palette is defined once as looked-up colors on `.root` in
`src/main/resources/css/kinetic.css`. Keep color literals out of Java and
other stylesheets.

## Typography

Syne is used for headings, labels and navigation. JetBrains Mono is reserved
for measurements, IDs, timestamps, protocol names and logs. The variable TTFs
and their OFL notices are bundled under `src/main/resources/fonts/`; JavaFX
loads the faces at application startup.

## Motion rules

`ui.motion.Motion` centralizes spring interpolation, scene entrance, stagger
timing and numeric tweening. `SpringInterpolator` is a damped spring with
configurable stiffness and damping. The shell's reduce-motion toggle replaces
spring interpolation with short linear fades and suppresses topology pulses.
Keep high-frequency rendering bounded: the topology layer caps active packets
at 40 and reuses pulse nodes.

## Component catalog

- `MetricCard`: threshold-aware numeric readout with warning, critical and
  offline states.
- `StatusChip`: compact protocol, machine and lifecycle state.
- `TelemetryChart`: lightweight JavaFX path chart for recent telemetry.
- `TopologyNode` and `PulseLayer`: system map nodes and pooled protocol packets.
- `LoginView`: JWT operator sign-in surface.

Views remain Java classes under `com.soa.desktop.view` and `com.soa.desktop.ui`.
There are no FXML documents or per-node inline styles.

## Adding a screen

1. Add a focused view class; keep protocol/business work in the existing client
   and orchestration layers.
2. Use shared components and existing CSS tokens rather than literal colors or
   local style declarations.
3. Add a navigation entry and a page factory in `MainApp`.
4. Dispose streaming clients, event subscriptions and timelines when leaving
   the screen.
5. Add demo data when a screen depends on a backend so `--demo` remains useful.

## Performance and verification

Service work stays off the JavaFX application thread; callbacks update controls
through `Platform.runLater`. Cache static topology paths and pulse shapes, avoid
effect animation on large panes, and keep telemetry histories bounded. Run:

```sh
mvn -f frontend/pom.xml -pl desktop-client -am clean verify
KT_DEMO=true mvn -f frontend/pom.xml -pl desktop-client clean org.openjfx:javafx-maven-plugin:0.0.8:run
```

Demo mode is available with `--demo` or `KT_DEMO=true`. A manual smoke pass
should check login rejection and success, shell navigation, live/demo
telemetry, alert acknowledgement, work-order transitions, SOAP stock and
reservation feedback, topology pulses, and reduce-motion behavior.

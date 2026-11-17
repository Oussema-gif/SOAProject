# Desktop UI Design System

This is the default desktop product interface and design system. The application
uses the same UI in normal operation; demo mode is an optional, explicitly
enabled testing aid and is not the product's default experience.

## Design tokens

| Token | Value | Use |
| --- | --- | --- |
| `-kt-bg` | `#080B10` | Application background |
| `-kt-surface-1` | `#111722` | Panels and cards |
| `-kt-surface-2` | `#151D2A` | Elevated controls and navigation |
| `-kt-text` | `#E8EEF7` | Primary text |
| `-kt-muted` | `#8292A8` | Supporting text and hairlines |
| `-kt-accent` | `#00E5F2` | Focus, telemetry and active navigation |
| `-kt-success` | `#00D68F` | Confirmed healthy state |
| `-kt-warning` | `#FFB547` | Warning or unverified state |
| `-kt-critical` | `#FF4560` | Critical or failed state |
| `-kt-offline` | `#59697D` | Disconnected or unchecked |

The palette is defined once as looked-up colors on `.root` in
`src/main/resources/css/kinetic.css`. Keep color literals out of Java and
other stylesheets.

## Navigation and workspaces

The application uses a full-width operations header and horizontal workspace
rail; it intentionally has no navigation sidebar. The seven workspaces are
Command Deck, Machine Fleet, Alert Register, Work Orders, Spare Parts
Inventory, Live Topology and Protocol Activity. `Ctrl+K` / `Cmd+K` opens the
keyboard-accessible command palette, with live machine, active-alert and
work-order search results when authenticated.

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

Telemetry charts plot only readings actually received in the current client
session and retain at most 72 samples. The backend exposes a live gRPC stream,
not a historical telemetry query or fleet-wide sensor aggregate. The inventory
WSDL exposes part lookup, stock check and reservation operations, but no parts
catalog-list or reservation-history operation; inventory is therefore looked
up by part ID and does not invent reserved-stock totals.

## Service checks and credentials

REST status comes from `/actuator/health`. SOAP status comes from an
authenticated `getSparePart` request for the seeded `PART-001`, so it checks
the configured key and inventory persistence. gRPC is live only after a
telemetry reading is received. PostgreSQL has no direct desktop health
connection; the topology reports it as indirectly observed, not independently
healthy. The optional demo mode marks connectivity as simulated rather than reporting
successful service health.

Set `INVENTORY_API_KEY` in the desktop process environment to the same value
configured for the SOAP service. `INVENTORY_WSDL_URL` optionally overrides
`http://localhost:8082/services/inventory.wsdl`; the SOAP operation endpoint
is derived from that WSDL URL. The client does not contain a fallback key and
does not weaken the service's API-key requirement.

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
5. Keep `--demo` data clearly marked as simulated; never surface it as live service health.

## Performance and verification

Service work stays off the JavaFX application thread; callbacks update controls
through `Platform.runLater`. Cache static topology paths and pulse shapes, avoid
effect animation on large panes, and keep telemetry histories bounded. Run:

```sh
mvn -f frontend/pom.xml -pl desktop-client -am clean verify
KT_DEMO=true mvn -f frontend/pom.xml -pl desktop-client clean org.openjfx:javafx-maven-plugin:0.0.8:run
```

Demo mode is available only when explicitly enabled with `--demo` or
`KT_DEMO=true`; it is a simulation, not a substitute for backend validation.
It is not enabled by default. The JavaFX Maven
launcher forwards `KT_DEMO` as the `kt.demo` JVM property so the documented
environment-variable launch works in its child process. A manual smoke pass
should check login rejection and success, horizontal workspace navigation,
`Ctrl+K` / `Cmd+K` command navigation and search, live/demo telemetry, alert
acknowledgement and resolution, paged alert/work-order registers, allowed
work-order transitions, SOAP stock lookup/reservation feedback with a valid
key, real topology health checks, protocol filters/pause, and reduce-motion
behavior.

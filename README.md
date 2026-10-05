# DriveLink Demo

An Android connected-car demo app. It is a test target for Perfecto (real devices and AI Scriptless), BlazeMeter service virtualization, BlazeMeter load tests, and GitHub Actions CI.

- [Phased plan](docs/PLAN.md)
- [Source design (architecture, scenario catalog)](docs/source-plan.md)
- [UI spec](docs/ui-spec.md)
- [API contract](docs/api.md)
- [Virtual service](docs/mock-service.md)
- [Decisions](docs/DECISIONS.md)

The app does not connect to a real vehicle or a real OEM backend. All backend calls go to a BlazeMeter virtual service.

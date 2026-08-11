# InVault PWA acceptance checklist

Run this checklist against the production HTTPS URL after DNS and Caddy are active. Use non-production inventory data and one account for every official role.

## Device matrix

| Platform                  | Minimum coverage       | Installation path               |
| ------------------------- | ---------------------- | ------------------------------- |
| Windows desktop or tablet | Current Microsoft Edge | Address bar installation action |
| Android tablet            | Current Chrome         | Browser installation action     |
| iPad                      | Current Safari         | Share → Add to Home Screen      |

Test tablet layouts in portrait and landscape. Record the device, OS version, browser version and result for every run.

## Installation and launch

- [ ] The application name is **InVault** and the shield/inventory icon is crisp.
- [ ] The installed application launches without browser chrome.
- [ ] Launching while signed out opens the login page.
- [ ] Launching while signed in opens the dashboard.
- [ ] Dashboard and Movements shortcuts open the expected route.
- [ ] Closing and reopening the application never exposes another user's session.

## Responsive and touch behaviour

- [ ] No page produces horizontal document scrolling in portrait or landscape.
- [ ] Navigation becomes a drawer below the desktop breakpoint.
- [ ] Product, batch, movement, catalogue and user forms fit inside the viewport.
- [ ] Wide data tables scroll only inside their own container.
- [ ] Buttons, row actions, paginator controls and form inputs are comfortable to operate by touch.
- [ ] The on-screen keyboard does not hide the active field or the form actions.

## Inventory flow

- [ ] Create or select catalogues and create a product.
- [ ] Create a batch and confirm that its quantity is read-only.
- [ ] Register an inbound stock movement and verify batch, stock and dashboard values.
- [ ] Register an outbound movement and verify the same values again.
- [ ] A second signed-in device receives the stock update without a manual reload.
- [ ] Audit records identify the actor and the before/after values.

## Roles

- [ ] `ADMIN` can use every authorized operation.
- [ ] `SUPERVISOR` sees only its authorized administration actions.
- [ ] `WAREHOUSE` can operate stock without accessing user administration.
- [ ] `READ_ONLY` cannot see or execute mutation actions.

## Connectivity and updates

- [ ] Disconnecting the device shows the global offline warning.
- [ ] Every create, update, deactivate, password and stock operation is rejected offline.
- [ ] Reconnecting removes the warning and restores the STOMP connection.
- [ ] Deploying a new frontend version shows the update notice.
- [ ] Accepting the update reloads once and preserves database data.
- [ ] The application shell can reopen during a short outage, without presenting cached inventory as current data.

## Acceptance

Release the installer/PWA only when all mandatory checks pass on the three platform rows. Attach screenshots and the recorded platform versions to the release notes for failures that need follow-up.

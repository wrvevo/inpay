# Product boundaries and homologation

## Current change
The existing React shell selects Gateway, Accounts, Marketplace, Credits or internal Billing; homologation retains its separate login realm. Existing hash routes and backend contracts are preserved. InPlay Games remains an external laboratory. This is navigation separation, not independent deployments or a security boundary.

## Ownership
- Gateway: payment processing, transaction query, routing, links, transaction risk and disputes.
- Accounts: merchant payment configuration and provider affiliation, banking relationships, settlement account references. Store only secret references in configuration; resolve credentials server-side.
- Marketplace: recipients, commercial split, settlement and payout.
- Credits: benefits and redemptions.
- Billing: internal pricing, invoicing and financial operations. Existing billing approval is not merchant KYC.
- Homologation: merchant/store onboarding, documentary review, configuration requests to Accounts, sandbox provisioning, test evidence and activation workflow.

## Production prerequisites (not implemented by this UI change)
1. Authenticated merchant dossier API, tenant-scoped authorization and durable state transitions. Never rely on sessionStorage or browser roles for authorization.
2. Private document ingestion: short-lived signed upload, allowlisted MIME and magic bytes, size limit, quarantine and malware scanning, encrypted object storage, metadata and checksums, authorized downloads, audit and version history. Define lawful purpose/basis, minimization and retention with privacy counsel; do not treat consent as universally required or the checklist as LGPD certification. Do not upload real documents to this laboratory.
3. Separate submitter and reviewer identities. Require verified active registration and approved document versions. Changes invalidate review. Capture reasons and evidence, with append-only audit.
4. Accounts owns versioned merchant/provider/method bindings; homologation requests provisioning via authenticated idempotent commands. A local approval flag is not provisioning confirmation. Use supported capability registry rather than assuming every provider supports every method.
5. Provision an isolated per-store sandbox configuration after cadastral approval and Accounts confirmation. Existing PHP InPlay Games uses environment configuration and is not yet provisioned per merchant. Never reuse production credentials or enable actual charging from demo.
6. Collect method-specific test evidence (approval, decline, cancellation/refund, timeout/UNKNOWN, idempotency, callback and reconciliation as applicable). Differentiate X-Mock and external sandbox. Only supported methods can advance. Mock success is not provider certification.
7. Production activation requires authorized technical approval, valid commercial credentials, provider confirmation and verified Accounts bindings; default deny and support suspension. No UI-only status can activate a store. Keep production and sandbox independent.

Suggested stages: DRAFT, DOCUMENT_REVIEW, DOCUMENT_PENDING, CADASTRAL_APPROVED, ACCOUNTS_PENDING, SANDBOX_READY, TESTING, TECHNICAL_REVIEW, READY_FOR_ACTIVATION, ACTIVE, SUSPENDED, REJECTED. Use versioned transitions, correlation IDs and outbox events. Do not change existing API contracts silently.

## Validation status
UI changes have not been built or run in this environment. The existing access.mjs lab workflow and its tests still need to be aligned with the document checklist and approval gates; do not merge until this is addressed. The current checklist is fictitious only and cannot validate documents. No production activation, real provisioning or new data collection is delivered.

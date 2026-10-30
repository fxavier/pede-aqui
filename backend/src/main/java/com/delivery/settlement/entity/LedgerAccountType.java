package com.delivery.settlement.entity;

/**
 * Chart of accounts of the settlement ledger (V065 CHECK; ADR-0005). The platform is a collection agent: customer
 * money it holds and what it owes merchants/couriers are liabilities; only commission and fees are its revenue.
 * The enum order is also the order in which accounts are resolved.
 */
public enum LedgerAccountType {
    /** Money received from the PSP/mock and not yet settled to the bank (debit-normal). */
    PLATFORM_CASH_CLEARING(Kind.ASSET, OwnerType.PLATFORM),
    /** Customer money held for orders not yet delivered (collection agent: not revenue). */
    CUSTOMER_FUNDS_HELD(Kind.LIABILITY, OwnerType.PLATFORM),
    /** Approved refunds not yet paid back to customers (no PSP payout yet, LAC-36). */
    CUSTOMER_REFUNDS_PAYABLE(Kind.LIABILITY, OwnerType.PLATFORM),
    /** What the platform owes one merchant (owner = vendor id). */
    MERCHANT_PAYABLE(Kind.LIABILITY, OwnerType.MERCHANT),
    /** What the platform owes one courier (owner = courier id) — no postings yet (courier pay, later). */
    COURIER_PAYABLE(Kind.LIABILITY, OwnerType.COURIER),
    /** Platform commission on the merchant's goods. */
    PLATFORM_REVENUE_COMMISSION(Kind.REVENUE, OwnerType.PLATFORM),
    /** Delivery + service fees charged to the customer. */
    PLATFORM_REVENUE_FEES(Kind.REVENUE, OwnerType.PLATFORM),
    /** Promotion discounts funded by the platform. */
    PLATFORM_PROMOTION_EXPENSE(Kind.EXPENSE, OwnerType.PLATFORM),
    /** VAT on the platform's own revenue (only when app.settlement.platform-vat-rate > 0). */
    PLATFORM_VAT_PAYABLE(Kind.LIABILITY, OwnerType.PLATFORM);

    /** Accounting nature: ASSET/EXPENSE are debit-normal, LIABILITY/REVENUE credit-normal. */
    public enum Kind { ASSET, LIABILITY, REVENUE, EXPENSE }

    /** Who the account belongs to; PLATFORM accounts have no owner id. */
    public enum OwnerType { PLATFORM, MERCHANT, COURIER }

    private final Kind kind;
    private final OwnerType ownerType;

    LedgerAccountType(Kind kind, OwnerType ownerType) {
        this.kind = kind;
        this.ownerType = ownerType;
    }

    public Kind kind() { return kind; }
    public OwnerType ownerType() { return ownerType; }

    /** True when the balance is read as debit − credit. */
    public boolean debitNormal() { return kind == Kind.ASSET || kind == Kind.EXPENSE; }
}

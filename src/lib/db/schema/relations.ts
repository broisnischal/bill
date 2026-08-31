import { defineRelations } from "drizzle-orm";

import * as schema from "./";

export const relations = defineRelations(schema, (r) => ({
  store: {
    owner: r.one.user({ from: r.store.ownerId, to: r.user.id }),
    members: r.many.storeMember({ from: r.store.id, to: r.storeMember.storeId }),
    customers: r.many.customer({ from: r.store.id, to: r.customer.storeId }),
    items: r.many.item({ from: r.store.id, to: r.item.storeId }),
    invoices: r.many.invoice({ from: r.store.id, to: r.invoice.storeId }),
  },
  storeMember: {
    store: r.one.store({ from: r.storeMember.storeId, to: r.store.id }),
    user: r.one.user({ from: r.storeMember.userId, to: r.user.id }),
  },
  customer: {
    store: r.one.store({ from: r.customer.storeId, to: r.store.id }),
    invoices: r.many.invoice({ from: r.customer.id, to: r.invoice.customerId }),
  },
  item: {
    store: r.one.store({ from: r.item.storeId, to: r.store.id }),
  },
  invoice: {
    store: r.one.store({ from: r.invoice.storeId, to: r.store.id }),
    customer: r.one.customer({ from: r.invoice.customerId, to: r.customer.id }),
    items: r.many.invoiceItem({ from: r.invoice.id, to: r.invoiceItem.invoiceId }),
    audits: r.many.invoiceAudit({ from: r.invoice.id, to: r.invoiceAudit.invoiceId }),
    payments: r.many.invoicePayment({ from: r.invoice.id, to: r.invoicePayment.invoiceId }),
    enteredBy: r.one.user({ from: r.invoice.enteredById, to: r.user.id }),
  },
  invoiceItem: {
    invoice: r.one.invoice({ from: r.invoiceItem.invoiceId, to: r.invoice.id }),
    item: r.one.item({ from: r.invoiceItem.itemId, to: r.item.id }),
  },
  invoicePayment: {
    invoice: r.one.invoice({ from: r.invoicePayment.invoiceId, to: r.invoice.id }),
    store: r.one.store({ from: r.invoicePayment.storeId, to: r.store.id }),
  },
  invoiceAudit: {
    invoice: r.one.invoice({ from: r.invoiceAudit.invoiceId, to: r.invoice.id }),
    actor: r.one.user({ from: r.invoiceAudit.actorId, to: r.user.id }),
  },
  device: {
    store: r.one.store({ from: r.device.storeId, to: r.store.id }),
    user: r.one.user({ from: r.device.userId, to: r.user.id }),
    leases: r.many.invoiceNumberLease({ from: r.device.id, to: r.invoiceNumberLease.deviceId }),
  },
  invoiceNumberLease: {
    store: r.one.store({ from: r.invoiceNumberLease.storeId, to: r.store.id }),
    device: r.one.device({ from: r.invoiceNumberLease.deviceId, to: r.device.id }),
  },
  shopperProfile: {
    user: r.one.user({ from: r.shopperProfile.userId, to: r.user.id }),
  },
  webLoginRequest: {
    approvedBy: r.one.user({ from: r.webLoginRequest.approvedByUserId, to: r.user.id }),
  },
  savedBill: {
    user: r.one.user({ from: r.savedBill.userId, to: r.user.id }),
    invoice: r.one.invoice({ from: r.savedBill.invoiceId, to: r.invoice.id }),
  },
}));

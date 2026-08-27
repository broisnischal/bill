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
    enteredBy: r.one.user({ from: r.invoice.enteredById, to: r.user.id }),
  },
  invoiceItem: {
    invoice: r.one.invoice({ from: r.invoiceItem.invoiceId, to: r.invoice.id }),
    item: r.one.item({ from: r.invoiceItem.itemId, to: r.item.id }),
  },
  invoiceAudit: {
    invoice: r.one.invoice({ from: r.invoiceAudit.invoiceId, to: r.invoice.id }),
    actor: r.one.user({ from: r.invoiceAudit.actorId, to: r.user.id }),
  },
}));

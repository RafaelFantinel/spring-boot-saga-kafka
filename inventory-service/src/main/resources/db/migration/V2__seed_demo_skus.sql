-- Demo stock so the sample curl flow in the README works out of the box.
INSERT INTO inventory (sku, available_qty, reserved_qty, version) VALUES
    ('SKU-1', 100, 0, 0),
    ('SKU-2', 50, 0, 0),
    ('SKU-LOW-STOCK', 1, 0, 0);

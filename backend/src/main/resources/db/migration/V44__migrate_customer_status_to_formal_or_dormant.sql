UPDATE customers
SET customer_status = 'FORMAL'
WHERE customer_status IS NULL
   OR customer_status = ''
   OR customer_status = 'INTENTION';

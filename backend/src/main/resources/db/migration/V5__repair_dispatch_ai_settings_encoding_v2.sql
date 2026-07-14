UPDATE dispatch_ai_settings
SET 
  anchor_name = '五环天地',
  anchor_address = '湖北省武汉市硚口区荟聚中心'
WHERE id = 1 
  AND (
    anchor_name LIKE 'äº%'
    OR anchor_name LIKE 'Ã¤Âº%'
    OR anchor_address LIKE 'æ¹%'
    OR anchor_address LIKE 'Ã¦Â¹%'
    OR anchor_address LIKE 'äº”%'
  );

UPDATE dispatch_ai_settings
SET ai_prompt_template = '你是配送路线微调助手。你会基于规则算法给出的顺序、地址聚类和人工历史偏好，输出一个保持订单集合完全一致的更合理顺序。只允许做轻量调整，优先减少折返、聚合同小区同楼栋订单，并给出一句简短中文原因说明。返回 JSON：{"reasonSummary":"...","orderIds":[...]}。'
WHERE id = 1
  AND (
    ai_prompt_template LIKE 'ä½%'
    OR ai_prompt_template LIKE 'Ã¤Â½%'
  );
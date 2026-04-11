TRUNCATE TABLE stage_templates;

-- 插入多类型的模板测试数据 (注意 tier 和 round)
INSERT INTO stage_templates (stage_id, tier, round, stage_type, map_name) VALUES
                                                                              ('T1_R1_NORMAL_A', 1, 1, 'NORMAL', 'test-map'),
                                                                              ('T1_R2_NORMAL_A', 1, 2, 'NORMAL', 'test-map'),
                                                                              ('T1_R2_EVENT_A',  1, 2, 'EVENT',  'event-map'), -- 事件房 (需准备对应地图文件夹)
                                                                              ('T1_R2_SHOP_A',   1, 2, 'SHOP',   'shop-map'),  -- 商店房
                                                                              ('T1_R3_ELITE_A',  1, 3, 'ELITE',  'test-map'),  -- 危险战斗房
                                                                              ('T1_R3_REST_A',   1, 3, 'REST',   'rest-map'),  -- 休息房
                                                                              ('T1_R5_BOSS',     1, 5, 'BOSS',   'test-map');
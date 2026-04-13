CREATE TABLE IF NOT EXISTS stage_templates (
    stage_id VARCHAR(64) PRIMARY KEY,
    tier INT NOT NULL,
    round INT NOT NULL,
    stage_type VARCHAR(32) NOT NULL,
    map_name VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS stage_mobs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    stage_id VARCHAR(64) NOT NULL,
    wave INT NOT NULL,
    mythicmobs_id VARCHAR(64) NOT NULL,
    amount INT NOT NULL,
    CONSTRAINT fk_stage_mobs_template
        FOREIGN KEY (stage_id) REFERENCES stage_templates(stage_id)
        ON DELETE CASCADE
);

DELETE FROM stage_mobs;
DELETE FROM stage_templates;

INSERT INTO stage_templates (stage_id, tier, round, stage_type, map_name) VALUES
('T1_R1_NORMAL_A', 1, 1, 'NORMAL', 'test-map'),
('T1_R1_REST_A', 1, 1, 'REST', 'rest-map'),
('T1_R2_NORMAL_A', 1, 2, 'NORMAL', 'test-map'),
('T1_R2_EVENT_A', 1, 2, 'EVENT', 'event-map'),
('T1_R2_SHOP_A', 1, 2, 'SHOP', 'shop-map'),
('T1_R3_NORMAL_A', 1, 3, 'NORMAL', 'test-map'),
('T1_R3_ELITE_A', 1, 3, 'ELITE', 'test-map'),
('T1_R3_REST_A', 1, 3, 'REST', 'rest-map'),
('T1_R4_ELITE_A', 1, 4, 'ELITE', 'test-map'),
('T1_R4_EVENT_A', 1, 4, 'EVENT', 'event-map'),
('T1_R4_NORMAL_A', 1, 4, 'NORMAL', 'test-map'),
('T1_R5_BOSS_A', 1, 5, 'BOSS', 'test-map');

INSERT INTO stage_mobs (stage_id, wave, mythicmobs_id, amount) VALUES
('T1_R1_NORMAL_A', 1, 'SkeletalKnight', 2),
('T1_R1_NORMAL_A', 2, 'AngrySludge', 1),

('T1_R2_NORMAL_A', 1, 'SkeletalKnight', 2),
('T1_R2_NORMAL_A', 2, 'SkeletalKnight', 1),
('T1_R2_NORMAL_A', 2, 'AngrySludge', 1),

('T1_R3_NORMAL_A', 1, 'SkeletalKnight', 3),
('T1_R3_NORMAL_A', 2, 'AngrySludge', 1),

('T1_R3_ELITE_A', 1, 'AngrySludge', 1),
('T1_R3_ELITE_A', 2, 'SkeletalKnight', 2),

('T1_R4_NORMAL_A', 1, 'SkeletalKnight', 2),
('T1_R4_NORMAL_A', 2, 'AngrySludge', 2),

('T1_R4_ELITE_A', 1, 'AngrySludge', 2),
('T1_R4_ELITE_A', 2, 'SkeletalKnight', 2),

('T1_R5_BOSS_A', 1, 'FuneralOfDeadButterflies', 1);

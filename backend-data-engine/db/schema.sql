-- Esquema de Base de Datos PropsBR v2.0 (Meta-Aggregator Ready)

CREATE TABLE IF NOT EXISTS teams (
    team_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    country TEXT,
    city TEXT,
    founded_year INTEGER,
    venue_name TEXT,
    logo_url TEXT,
    last_updated INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS competitions (
    competition_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    country TEXT,
    type TEXT,
    logo_url TEXT,
    last_updated INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS matches (
    match_id TEXT PRIMARY KEY,
    competition_id TEXT NOT NULL,
    season INTEGER NOT NULL,
    match_date TEXT NOT NULL,
    home_team_id TEXT NOT NULL,
    away_team_id TEXT NOT NULL,
    home_goals INTEGER,
    away_goals INTEGER,
    status TEXT DEFAULT 'SCHEDULED',
    home_xg REAL,
    away_xg REAL,
    home_shots INTEGER,
    away_shots INTEGER,

    -- Meta-Agregación (Forebet, SofaScore)
    forebet_score TEXT,
    forebet_prob_1 REAL,
    forebet_prob_x REAL,
    forebet_prob_2 REAL,
    sofa_votes_home REAL,
    sofa_votes_draw REAL,
    sofa_votes_away REAL,

    last_updated INTEGER DEFAULT 0,
    FOREIGN KEY (home_team_id) REFERENCES teams(team_id),
    FOREIGN KEY (away_team_id) REFERENCES teams(team_id),
    FOREIGN KEY (competition_id) REFERENCES competitions(competition_id)
);

CREATE TABLE IF NOT EXISTS player_match_stats (
    match_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    team_id TEXT NOT NULL,
    minutes_played INTEGER DEFAULT 0,
    shots_total INTEGER DEFAULT 0,
    shots_on_target INTEGER DEFAULT 0,
    key_passes INTEGER DEFAULT 0,
    rating REAL,
    last_update INTEGER DEFAULT 0,
    PRIMARY KEY (match_id, player_id),
    FOREIGN KEY (match_id) REFERENCES matches(match_id)
);

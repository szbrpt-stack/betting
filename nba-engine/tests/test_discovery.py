import pytest
from nba_engine.discovery import DailyDiscovery, DiscoveryError

def event():
    return {"id":"evt-1","sport":"basketball","starts_at":"2026-11-17T23:30:00Z",
            "participants":["NYK","TOR"],"sources":[{"url":"https://example.org/schedule",
            "retrieved_at":"2026-11-17T14:00:00Z"}]}

def test_discover_cache_and_questions(tmp_path):
    store=DailyDiscovery(tmp_path/"daily.sqlite")
    calls=[]
    def research(prompt):
        calls.append(prompt)
        return {"events":[event()]}
    first=store.discover(day="2026-11-17",timezone_name="America/Bogota",research=research)
    second=store.discover(day="2026-11-17",timezone_name="America/Bogota",research=research)
    assert len(calls)==1
    assert not first["cache_hit"] and second["cache_hit"]
    assert first["count"]==1
    assert len(store.questions(first["events"][0]))==6
    store.discover(day="2026-11-17",timezone_name="America/Bogota",research=research,refresh=True)
    assert len(calls)==2

def test_different_local_day_rejected(tmp_path):
    store=DailyDiscovery(tmp_path/"daily.sqlite")
    with pytest.raises(DiscoveryError):
        store.discover(day="2026-11-18",timezone_name="America/Bogota",
                       research=lambda _: {"events":[event()]})

def test_source_required(tmp_path):
    store=DailyDiscovery(tmp_path/"daily.sqlite")
    e=event()
    e["sources"]=[]
    with pytest.raises(DiscoveryError):
        store.discover(day="2026-11-17",timezone_name="America/Bogota",
                       research=lambda _: {"events":[e]})

def test_no_events_is_valid(tmp_path):
    store=DailyDiscovery(tmp_path/"daily.sqlite")
    assert store.discover(day="2026-11-17",timezone_name="America/Bogota",
                          research=lambda _: {"events":[]})["count"]==0

def test_unvalidated_probability_cannot_be_saved(tmp_path):
    store=DailyDiscovery(tmp_path/"daily.sqlite")
    analysis={"event_id":"evt-1","question_id":"basketball.points.recent",
              "subject_id":"NYK","as_of":"2026-11-17T14:00:00Z",
              "status":"descriptive_only","target":{},"observed":{},
              "prediction":{"event_probability":0.8},"evidence":[],
              "audit":{"formula_version":"sigma-q/1"}}
    with pytest.raises(DiscoveryError):
        store.save_analysis(analysis)
    analysis["prediction"]["event_probability"]=None
    store.save_analysis(analysis)
    assert store.get_analysis("evt-1","basketball.points.recent","NYK",
                              "2026-11-17T14:00:00Z")["status"]=="descriptive_only"

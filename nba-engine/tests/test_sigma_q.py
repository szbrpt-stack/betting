import pytest
from nba_engine.sigma_q import analyze, question_catalog

EVENT = {"id":"future","sport":"basketball","starts_at":"2026-11-18T20:00:00Z","participants":["A","B"]}

def q():
    return question_catalog(EVENT)[0]

def rows(n=10):
    return [{"event_id":f"g{i}","sport":"basketball","subject_id":"A",
             "status":"completed","completed_at":f"2026-11-{i+1:02d}T21:00:00Z",
             "venue":"home" if i%2 else "away","unit":"points","value":100+i,
             "source_id":f"src{i}","source_url":f"https://example.org/{i}"}
            for i in range(n)]

def test_catalog_multiple_participants_and_sports():
    assert len(question_catalog(EVENT)) == 6
    race = dict(EVENT,sport="motorsport",participants=["A","B","C"])
    assert len(question_catalog(race)) == 6

def test_descriptive_and_no_fabricated_prediction():
    result=analyze(EVENT,q(),rows(),as_of="2026-11-18T19:00:00Z")
    assert result["status"]=="descriptive_only"
    assert result["observed"]["mean"]==104.5
    assert result["prediction"]["event_probability"] is None
    assert len(result["evidence"])==10

def test_threshold_is_observed_not_prediction():
    question=q()
    question["target"]["operator"]="gt"
    question["target"]["threshold"]=105
    result=analyze(EVENT,question,rows(),as_of="2026-11-18T19:00:00Z")
    assert result["observed"]["observed_hit_rate"]==.4
    assert result["prediction"]["event_probability"] is None

def test_cutoff_excludes_future_and_incomplete():
    sample=rows()
    sample[0]["completed_at"]="2026-11-18T19:00:00Z"
    sample[1]["status"]="scheduled"
    result=analyze(EVENT,q(),sample,as_of="2026-11-18T19:00:00Z")
    assert result["observed"]["sample_n"]==8

def test_insufficient_evidence_returns_null():
    result=analyze(EVENT,q(),rows(3),as_of="2026-11-18T19:00:00Z")
    assert result["status"]=="insufficient_evidence"
    assert result["observed"]["mean"] is None

@pytest.mark.parametrize("change",[
    {"unit":"goals"},{"value":float("nan")},{"value":-1},
])
def test_invalid_or_missing_observations(change):
    sample=rows()
    sample[0].update(change)
    if "value" in change and change["value"]!= -1:
        assert analyze(EVENT,q(),sample,as_of="2026-11-18T19:00:00Z")["observed"]["sample_n"]==9
    else:
        with pytest.raises(ValueError):
            analyze(EVENT,q(),sample,as_of="2026-11-18T19:00:00Z")

def test_duplicate_fails_closed():
    sample=rows()
    sample.append(dict(sample[0]))
    with pytest.raises(ValueError):
        analyze(EVENT,q(),sample,as_of="2026-11-18T19:00:00Z")

def test_postgame_cutoff_rejected():
    with pytest.raises(ValueError):
        analyze(EVENT,q(),rows(),as_of="2026-11-19T00:00:00Z")

def test_wrong_subject_rejected():
    question=q()
    question["subject_id"]="Z"
    with pytest.raises(ValueError):
        analyze(EVENT,question,rows(),as_of="2026-11-18T19:00:00Z")

def test_home_away_filter():
    question=q()
    question["scope"]["venue"]="home"
    result=analyze(EVENT,question,rows(),as_of="2026-11-18T19:00:00Z")
    assert result["observed"]["sample_n"]==5
    assert result["observed"]["mean"]==105

def test_between_operator():
    question=q()
    question["target"].update(operator="between",threshold=[103,106])
    result=analyze(EVENT,question,rows(),as_of="2026-11-18T19:00:00Z")
    assert result["observed"]["observed_hit_rate"]==.4

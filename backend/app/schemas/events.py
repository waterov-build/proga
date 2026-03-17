from pydantic import BaseModel


class EventCreate(BaseModel):
    name: str


class EventRead(BaseModel):
    model_config = {"from_attributes": True}

    id: str
    name: str

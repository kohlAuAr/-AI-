-- Initial schema for NEW MySQL databases only. Existing H2 data requires explicit import.
create table ai_conversation_owner (created_at datetime(6), owner_id bigint not null, id varchar(36) not null, primary key (id)) engine=InnoDB;
create table conversation_turn (created_at datetime(6), id bigint not null auto_increment, conversation_id varchar(36) not null, mode varchar(255), retrieval varchar(255), answer longtext not null, question longtext not null, references_json longtext not null, steps_json longtext, primary key (id)) engine=InnoDB;
create table interest_vector (content_key varchar(64) not null, vector_json longtext not null, primary key (content_key)) engine=InnoDB;
create table knowledge_chunk (chunk_number integer not null, document_id bigint not null, id bigint not null auto_increment, content longtext not null, vector_json longtext, primary key (id)) engine=InnoDB;
create table knowledge_document (chunk_count integer not null, club_id bigint, created_at datetime(6), id bigint not null auto_increment, uploaded_by bigint, fingerprint varchar(64) not null, embedding_version varchar(500) not null, name varchar(255) not null, visibility varchar(255), content longtext not null, primary key (id)) engine=InnoDB;
create table platform_model_profile (activated_at datetime(6), activated_by bigint, revision bigint not null, tested_revision bigint, purpose varchar(255) not null, active_json longtext, draft_json longtext, primary key (purpose)) engine=InnoDB;
create index IDXexoc5ih47bpngxi156rnh0jgg on conversation_turn (conversation_id);
alter table knowledge_chunk add constraint UKh4ovfmehcc8mvi4oo9tkl1yy5 unique (document_id, chunk_number);
alter table knowledge_document add constraint UKcg834fxril6nhy7cu71xsw1xc unique (fingerprint);
alter table knowledge_chunk add constraint FK7frrd7r3hf9ckbasd13w0xfse foreign key (document_id) references knowledge_document (id);

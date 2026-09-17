-- Create explicit revinfo_seq sequence for Hibernate Envers revision generation
-- Hibernate Envers by default tries to use a sequence to generate revision IDs
-- The revinfo table uses GENERATED ALWAYS AS IDENTITY, but we also create this explicit sequence
-- to satisfy Hibernate's attempt to call nextval('revinfo_seq') during revision generation.
-- Both the identity and sequence work independently without conflict in PostgreSQL.

CREATE SEQUENCE IF NOT EXISTS revinfo_seq START WITH 1 INCREMENT BY 1;



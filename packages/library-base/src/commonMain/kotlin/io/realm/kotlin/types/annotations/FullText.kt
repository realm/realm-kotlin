package io.realm.kotlin.types.annotations

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
@MustBeDocumented
/**
 * Annotation marking a property as having a full-text index, which will enable full-text queries on
 * it, which can be queried using the `TEXT` query syntax, e.g.
 * `realm.query<Person>("bio TEXT 'computer dancing'").find()`.
 *
 * Only [String] properties can be marked with this annotation and it cannot be combined with the
 * [Index] annotation.
 *
 * The full-text index currently support this set of features:
 *
 * - Only token search is supported, e.g. `bio TEXT 'computer dancing'`, will find all objects
 *   that are mentioning `computer` and `dancing` in their `bio` property.
 * - Tokens are diacritics- and case-insensitive, `bio TEXT 'cafe dancing'` and
 *   `bio TEXT 'café DANCING'` will return the same set of matches.
 * - Ignoring results with words are done using `-`, e.g. `bio TEXT 'computer -dancing'`.
 * - Words are defined by a simple tokenizer that uses the following rules:
 *   - Tokens can only consist of alphanumerical characters from ASCII and the Latin-1 supplement.
 *   - All other characters are considered whitespace. In particular words using `-` like
 *     `full-text` are split into two tokens.
 *
 * In particular, note the following constraints before using full-text search:
 *
 * - Token prefix or suffix search like `bio TEXT 'comp* *cing'` is not supported.
 * - Negative only searches like `bio TEXT '-dancing'` is not supported, at least one included
 *   word must be provided.
 * - Only ASCII and Latin-1 alphanumerical chars are included in the index (most western languages).
 * - Only boolean match is supported, i.e. it isn't possible to sort results by "relevance".
 */
public annotation class FullText

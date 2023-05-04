package io.realm.kotlin.types.annotations

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
@MustBeDocumented
/**
 * Annotation marking a property as having a full-text index, which will enable full-text queries on
 * it. Full-text searches are done using the `TEXT` predicate, e.g.:
 *
 * ```
 * realm.query<Person>("bio TEXT 'computer dancing'").find()`
 * ```
 *
 * Only [String] properties can be marked with this annotation and it cannot be combined with the
 * [Index] annotation.
 *
 * The full-text index currently support this set of features:
 *
 * - Only token or word search, e.g. `bio TEXT 'computer dancing'` will find all objects that
 *   contains the words `computer` and `dancing` in their `bio` property.
 * - Tokens are diacritics- and case-insensitive, e.g.`bio TEXT 'cafe dancing'` and
 *   `bio TEXT 'café DANCING'` will return the same set of matches.
 * - Ignoring results with certain tokens are done using `-`, e.g. `bio TEXT 'computer -dancing'`
 *   will find all objects that contain `computer` but not `dancing`.
 * - Tokens are defined by a simple tokenizer that uses the following rules:
 *   - Tokens can only consist of alphanumerical characters from ASCII and the Latin-1 supplement.
 *   - All other characters are considered whitespace. In particular words using `-` like
 *     `full-text` are split into two tokens.
 *
 * Note the following constraints before using full-text search:
 *
 * - Token prefix or suffix search like `bio TEXT 'comp* *cing'` is not supported.
 * - Only ASCII and Latin-1 alphanumerical chars are included in the index (most western languages).
 * - Only boolean match is supported, i.e. "found" or "not found". It is not possible to sort
 *   results by "relevance" .
 */
public annotation class FullText

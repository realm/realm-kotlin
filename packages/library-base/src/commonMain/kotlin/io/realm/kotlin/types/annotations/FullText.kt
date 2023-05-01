package io.realm.kotlin.types.annotations

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FIELD)
@MustBeDocumented
/**
 * Annotation marking a property as having a full-text index, which will enable full-text queries on
 * it, which can be queried using the `TEXT` query syntax, e.g.
 * `realm.query<Person>("bio TEXT 'computer dancing -'").find()`.
 *
 * Only [String] properties can be marked with this annotation and it cannot be combined with the
 * [Index] annotation.
 *
 * The full-text index currently support this set of features:
 *
 * - Only token search is supported, e.g. `bio TEXT 'computer dancing'`.
 * - Tokens are case-insensitive, `bio TEXT 'computer dancing'` and `bio TEXT 'COMPUTER DANCING'`
 *   will return the same matches.
 * - Ignoring results with words are done using `-`, e.g. `bio TEXT 'computer -dancing'`.
 * - The full-text implementation uses a simple tokenizer with the following rules:
 *   - TODO Get details
 *   - TODO Get details
 *
 * In particular, note the following constraints before using full-text search:
 *
 * - Token prefix or suffix search like `bio TEXT 'comp* *cing'` is not supported.
 * - Negative only searches like `bio TEXT '-dancing'` is not supported, at least one included
 *   token must be provided.
 * - Only ASCII chars are supported. TODO Is it is a subset of ascii?
 * - Only boolean match is supported, i.e. it isn't possible to sort results by "relevance".
 */
public annotation class FullText

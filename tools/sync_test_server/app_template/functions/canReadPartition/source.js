/**
 * Users with an email that contains `_noread_` do not have read access,
 * all others do.
 */
exports = async (partition) => {
  if (email != undefined) {
    return !email.includes("_noread_");
  } else {
    return true;
  }
}
/**
 * Users with an email that contains `_nowrite_` do not have write access,
 * all others do.
 */
exports = async (partition) => {
  if (email != undefined) {
    return(!email.includes("_nowrite_"));
  } else {
    return true;
  }  
}
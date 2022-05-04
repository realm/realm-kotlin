
  /*

    This function will be run AFTER a user registers their username and password and is called with an object parameter
    which contains three keys: 'token', 'tokenId', and 'username'.

    The return object must contain a 'status' key which can be empty or one of three string values: 
      'success', 'pending', or 'fail'.

    'success': the user is confirmed and is able to log in.

    'pending': the user is not confirmed and the UserPasswordAuthProviderClient 'confirmUser' function would 
      need to be called with the token and tokenId via an SDK. (see below)

      const emailPassClient = Stitch.defaultAppClient.auth
        .getProviderClient(UserPasswordAuthProviderClient.factory);

      return emailPassClient.confirmUser(token, tokenId);

    'fail': the user is not confirmed and will not be able to log in.

    If an error is thrown within the function the result is the same as 'fail'.

    Example below:

    exports = ({ token, tokenId, username }) => {
      // process the confirm token, tokenId and username
      if (context.functions.execute('isValidUser', username)) {
        // will confirm the user
        return { status: 'success' };
      } else {
        context.functions.execute('sendConfirmationEmail', username, token, tokenId);
        return { status: 'pending' };
      }
  
      return { status: 'fail' };
    };

    The uncommented function below is just a placeholder and will result in failure.
  */
exports = async ({ token, tokenId, username }) => {
    // process the confirm token, tokenId and username

    if (username.includes("realm_verify")) {
      // Automatically confirm users with `realm_verify` in their email.
      return { status: 'success' }
    } else if (username.includes("realm_pending")) {
      // Emails with `realm_pending` in their email will be placed in Pending
      // the first time they register and will then be fully confirmed when
      // they retry their their confirmation logic.
      const mdb = context.services.get("BackingDB");
      const collection = mdb.db("custom-auth").collection("users");
      const existing = await collection.findOne({ username: username });
      if (existing) {
          return { status: 'success' };
      }
      await collection.insertOne({ username: username });
      return { status: 'pending' }
    } else {
      // All other emails should fail to confirm outright.
      return { status: 'fail' };
    }
  };
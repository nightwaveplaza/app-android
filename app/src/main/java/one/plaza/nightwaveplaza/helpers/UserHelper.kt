package one.plaza.nightwaveplaza.helpers

object UserHelper {
    fun setToken(token: String) {
        StorageHelper.save(Keys.USER_TOKEN, token)
    }

    fun getToken(): String? {
        return StorageHelper.load(Keys.USER_TOKEN, "")
    }

    fun isLogged(): Boolean {
        val token = getToken()
        return !token.isNullOrEmpty()
    }
}
package one.plaza.nightwaveplaza.helpers

object UserHelper {
    fun setToken(token: String) {
        StorageHelper.save(PrefKeys.USER_TOKEN, token)
    }

    fun getToken(): String? {
        return StorageHelper.load(PrefKeys.USER_TOKEN, "")
    }

    fun isLogged(): Boolean {
        val token = getToken()
        return !token.isNullOrEmpty()
    }
}
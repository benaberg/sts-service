package fi.benaberg.sts.service.def

class HttpResponse {

    companion object {
        const val OK: Int = 200
        const val BAD_REQUEST: Int = 400
        const val NOT_FOUND: Int = 404
        const val INTERNAL_SERVER_ERROR: Int = 500
    }
}
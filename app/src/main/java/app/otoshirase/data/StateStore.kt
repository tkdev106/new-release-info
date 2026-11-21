package app.otoshirase.data

/** 保存データの読み書き。判定のテストで端末のファイルを使わずに差し替えるため */
interface StateStore {
    fun current(): AppState
    suspend fun update(transform: (AppState) -> AppState)
}

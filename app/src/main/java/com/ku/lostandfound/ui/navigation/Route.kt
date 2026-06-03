package com.ku.lostandfound.ui.navigation

sealed class Route(val route: String) {
    /* 로그인, 회원가입 */
    object Login : Route(route = "login")
    object SignupCode : Route(route = "SignupCode")
    object SignupEmail : Route(route = "SignupEmail")
    object SignupFinish : Route(route = "SignupFinish")
    object SignupName : Route(route = "SignupName")
    object SignupPw : Route(route = "SignupPw")

    /* 메인 탭 */
    object Found : Route(route = "found")
    object Lost : Route(route = "lost")
    object Profile : Route(route = "profile")
    object MyLostPosts : Route(route = "myPosts/lost")
    object MyFoundPosts : Route(route = "myPosts/found")

    /* 검색 */
    object Search : Route(route = "search")

    /* 게시글 */
    object PostWrite : Route(route = "postWrite")
    object PostDetail : Route(route = "postDetail/{postId}") {
        const val ARG_POST_ID = "postId"
        fun create(postId: String): String = "postDetail/$postId"
    }

    /* 위치 추가 */
    object LocationPicker : Route(route = "locationPicker/{postType}") {
        const val ARG_POST_TYPE = "postType"
        fun create(postTypeName: String): String = "locationPicker/$postTypeName"
    }
}

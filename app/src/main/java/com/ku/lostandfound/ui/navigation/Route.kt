package com.ku.lostandfound.ui.navigation

sealed class Route(val route: String) {
    object Login : Route(route = "login")
    object SignupCode : Route(route = "SignupCode")
    object SignupEmail : Route(route = "SignupEmail")
    object SignupFinish : Route(route = "SignupFinish")
    object SignupName : Route(route = "SignupName")
    object SignupPw : Route(route = "SignupPw")

    object Found : Route(route = "found")
    object Lost : Route(route = "lost")
    object Profile : Route(route = "profile")
    object MyLostPosts : Route(route = "myPosts/lost")
    object MyFoundPosts : Route(route = "myPosts/found")

    object Search : Route(route = "search")

    object PostWrite : Route(route = "postWrite")
    object PostEdit : Route(route = "postEdit/{postId}") {
        const val ARG_POST_ID = "postId"
        fun create(postId: String): String = "postEdit/$postId"
    }
    object PostDetail : Route(route = "postDetail/{postType}/{postId}") {
        const val ARG_POST_TYPE = "postType"
        const val ARG_POST_ID = "postId"
        fun create(postId: String, postTypeName: String): String = "postDetail/$postTypeName/$postId"
    }

    object LocationPicker : Route(route = "locationPicker/{postType}") {
        const val ARG_POST_TYPE = "postType"
        fun create(postTypeName: String): String = "locationPicker/$postTypeName"
    }
}

package np.bill.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import np.bill.data.prefs.AppMode
import np.bill.ui.auth.OtpScreen
import np.bill.ui.auth.SignInScreen
import np.bill.ui.auth.AuthViewModel
import np.bill.ui.billing.BillDetailScreen
import np.bill.ui.billing.NewBillScreen
import np.bill.ui.mode.ModePickerScreen
import np.bill.ui.onboarding.RegisterBusinessScreen

object Routes {
  const val SIGN_IN = "sign-in"
  const val OTP = "otp/{phone}"
  const val MODE = "mode"
  const val REGISTER = "register"
  const val HOME = "home"
  const val NEW_BILL = "bill/new?items={itemIds}&customer={customerId}"
  const val BILL = "bill/{billId}"
  const val WALLET = "wallet"

  fun otp(phone: String) = "otp/$phone"

  fun newBill(itemIds: List<String> = emptyList(), customerId: String? = null) =
    "bill/new?items=${itemIds.joinToString(",")}&customer=${customerId.orEmpty()}"
  fun bill(id: String) = "bill/$id"
}

/**
 * Where the app opens.
 *
 * The route is decided by what the person has already done, not by a splash screen: no
 * session goes to sign-in, no chosen mode goes to the mode picker, a business with no
 * registration goes to the form, and everyone else lands on the screen they use all day.
 */
@Composable
fun BillApp(deepLinkToken: String? = null) {
  val viewModel: RootViewModel = hiltViewModel()
  val state by viewModel.state.collectAsStateWithLifecycle()
  val navController = rememberNavController()

  // Signing in spans two screens, and they have to be the same view model: the number
  // typed on the first is what the second resends to. Held here so both share one.
  val authViewModel: AuthViewModel = hiltViewModel()

  // A blank frame before the start route is known reads as a flash. The window is
  // already painted in the theme's background, so holding it costs nothing visible.
  if (!state.ready) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    return
  }

  val start = when {
    !state.signedIn -> Routes.SIGN_IN
    !state.modeChosen -> Routes.MODE
    state.mode == AppMode.CUSTOMER -> Routes.WALLET
    !state.hasStore -> Routes.REGISTER
    else -> Routes.HOME
  }

  NavHost(
    navController = navController,
    startDestination = start,
    // Symmetrical on purpose. Sliding in while the outgoing screen only faded left a
    // frame where neither filled the space, which is what the flicker was.
    enterTransition = {
      fadeIn(tween(FADE_MS)) + slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        tween(SLIDE_MS),
        initialOffset = { it / 12 },
      )
    },
    exitTransition = {
      fadeOut(tween(FADE_MS)) + slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.Start,
        tween(SLIDE_MS),
        targetOffset = { it / 12 },
      )
    },
    popEnterTransition = {
      fadeIn(tween(FADE_MS)) + slideIntoContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        tween(SLIDE_MS),
        initialOffset = { it / 12 },
      )
    },
    popExitTransition = {
      fadeOut(tween(FADE_MS)) + slideOutOfContainer(
        AnimatedContentTransitionScope.SlideDirection.End,
        tween(SLIDE_MS),
        targetOffset = { it / 12 },
      )
    },
  ) {
    composable(Routes.SIGN_IN) {
      SignInScreen(
        onCodeSent = { phone -> navController.navigate(Routes.otp(phone)) },
        viewModel = authViewModel,
      )
    }

    composable(Routes.OTP) { entry ->
      OtpScreen(
        phoneNumber = entry.arguments?.getString("phone").orEmpty(),
        onVerified = { navController.replaceWith(Routes.MODE) },
        onBack = navController::popBackStack,
        viewModel = authViewModel,
      )
    }

    composable(Routes.MODE) {
      ModePickerScreen(
        onBusiness = { hasStore ->
          navController.replaceWith(if (hasStore) Routes.HOME else Routes.REGISTER)
        },
        onCustomer = { navController.replaceWith(Routes.WALLET) },
      )
    }

    composable(Routes.REGISTER) {
      RegisterBusinessScreen(onRegistered = { navController.replaceWith(Routes.HOME) })
    }

    composable(Routes.HOME) {
      BusinessHome(
        onNewBill = { itemIds, customerId ->
          navController.navigate(Routes.newBill(itemIds, customerId))
        },
        onOpenBill = { navController.navigate(Routes.bill(it)) },
        onSwitchMode = { navController.replaceWith(Routes.MODE) },
        onSignedOut = { navController.replaceWith(Routes.SIGN_IN) },
      )
    }

    composable(
      Routes.NEW_BILL,
      arguments = listOf(
        navArgument("itemIds") { defaultValue = "" },
        navArgument("customerId") { defaultValue = "" },
      ),
    ) { entry ->
      NewBillScreen(
        startItemIds = entry.arguments?.getString("itemIds")
          ?.split(",")
          ?.filter(String::isNotBlank)
          .orEmpty(),
        startCustomerId = entry.arguments?.getString("customerId")?.ifBlank { null },
        onDone = { billId ->
          navController.popBackStack()
          navController.navigate(Routes.bill(billId))
        },
        onBack = navController::popBackStack,
      )
    }

    composable(Routes.BILL) { entry ->
      BillDetailScreen(
        billId = entry.arguments?.getString("billId").orEmpty(),
        onBack = navController::popBackStack,
      )
    }

    composable(Routes.WALLET) {
      CustomerHome(
        deepLinkToken = deepLinkToken,
        onSwitchMode = { navController.replaceWith(Routes.MODE) },
        onSignedOut = { navController.replaceWith(Routes.SIGN_IN) },
      )
    }
  }
}

/** Moves on and drops what came before, for the one-way steps of getting set up. */
private fun NavHostController.replaceWith(route: String) {
  navigate(route) {
    popUpTo(graph.startDestinationId) { inclusive = true }
    launchSingleTop = true
  }
}

/**
 * A short slide under a slightly shorter fade. The offset is a twelfth of the width
 * rather than the whole of it: a full-width slide on a 6-inch phone is a long time to
 * look at nothing, and it is what made moving around the app feel unsettled.
 */
private const val SLIDE_MS = 260
private const val FADE_MS = 160

package com.fnphoto.tv

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as lazyGridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as lazyGridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import com.fnphoto.tv.api.FnAuthUtils
import com.fnphoto.tv.api.FnHttpApi
import com.fnphoto.tv.api.HttpClientProvider
import com.fnphoto.tv.browse.DisplayBrowseLifecycle
import com.fnphoto.tv.browse.DisplayBrowseLoadState
import com.fnphoto.tv.browse.DisplayBrowseRepository
import com.fnphoto.tv.browse.DisplayCategorySource
import com.fnphoto.tv.browse.DisplayTagIdentity
import com.fnphoto.tv.browse.LoadPublicationGate
import com.fnphoto.tv.cache.CachedImageLoader
import com.fnphoto.tv.home.PhotoStreamGrouper
import com.fnphoto.tv.home.PhotoStreamGroup
import com.fnphoto.tv.home.PhotoStreamMapper
import com.fnphoto.tv.home.PhotoStreamPhoto
import com.fnphoto.tv.home.PhotoStreamRenderKeys
import com.fnphoto.tv.settings.SessionPreferences
import com.fnphoto.tv.settings.UserProfileStore
import com.fnphoto.tv.ui.HeroHost
import com.fnphoto.tv.ui.TopBarMetrics
import com.fnphoto.tv.ui.TvNavItem
import com.fnphoto.tv.ui.TvNavigation
import com.fnphoto.tv.update.AppReleaseInfo
import com.fnphoto.tv.update.AppUpdateCheckResult
import com.fnphoto.tv.update.AppUpdateChecker
import com.fnphoto.tv.update.AppUpdateInstaller
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ProjectionBlack = Color(0xFF10100E)
private val ContactSheet = Color(0xFF1B1A16)
private val ContactSheetRaised = Color(0xFF24231F)
private val AlbumPaper = Color(0xFFEDE5D2)
private val SilverGelatin = Color(0xFF8A8276)
private val FocusMint = Color(0xFF6ECFC1)
private val DateAmber = Color(0xFFD7B56D)
private val NegativeCoral = Color(0xFFE06C5C)
private val TopNavHeight = 128.dp
private val ContentHorizontalPadding = 48.dp
private val ContentTopPadding = 136.dp

private data class FnTvThemeColors(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val focus: Color,
    val accent: Color,
    val negative: Color,
    val topBarStart: Color,
    val topBarMid: Color,
    val loadingOverlay: Color,
)

private val DarkTvThemeColors = FnTvThemeColors(
    background = ProjectionBlack,
    surface = ContactSheet,
    surfaceRaised = ContactSheetRaised,
    primaryText = AlbumPaper,
    secondaryText = SilverGelatin,
    focus = FocusMint,
    accent = DateAmber,
    negative = NegativeCoral,
    topBarStart = Color(0xF210100E),
    topBarMid = Color(0xC010100E),
    loadingOverlay = Color(0x9910100E),
)

private val WhiteTvThemeColors = FnTvThemeColors(
    background = Color(0xFFF3F1EA),
    surface = Color(0xFFE8E4DA),
    surfaceRaised = Color(0xFFFFFFFF),
    primaryText = Color(0xFF171714),
    secondaryText = Color(0xFF706A60),
    focus = Color(0xFF2E7D73),
    accent = Color(0xFF9A6B20),
    negative = Color(0xFFB54B43),
    topBarStart = Color(0xF2F3F1EA),
    topBarMid = Color(0xD8F3F1EA),
    loadingOverlay = Color(0xAAEEEAE1),
)

private val LocalFnTvThemeColors = staticCompositionLocalOf { DarkTvThemeColors }

class MainActivity : FragmentActivity(), HeroHost {
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val backHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private lateinit var profileStore: UserProfileStore
    private var api: FnHttpApi? = null
    private var displayBrowseRepository: DisplayBrowseRepository? = null
    private val displayBrowseLifecycle = DisplayBrowseLifecycle()
    private val loadPublicationGate = LoadPublicationGate<String>()
    private var currentDisplayBrowseOwner: DisplayBrowseLifecycle.Owner? = null
    private var currentDirectLoadOwner: LoadPublicationGate.Owner<String>? = null
    private var baseUrl: String = ""
    private var token by mutableStateOf("")
    private var secret: String = ""
    private var homeLoadJob: Job? = null
    private var homePageJob: Job? = null
    private var collectionJob: Job? = null
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var legacyFragment: MainFragment? = null
    private var lastLegacyAction: String? = null
    private var currentHomePhotos: List<PhotoStreamPhoto> = emptyList()
    private var currentStreamSource = StreamSource.gallery()
    private var galleryTimelineItems: List<TimelineBucket> = emptyList()
    private var galleryTimelineCursor = 0
    private var galleryDateOffset = 0
    private var directPhotoOffset = 0
    private var galleryHasMore = false
    private val pageBackStack = MainPageBackStack<MainPageRoute, MainFocusSnapshot>()
    private var currentRoute: MainPageRoute = MainPageRoute.Gallery
    private var currentFocusSnapshot: MainFocusSnapshot? = MainFocusSnapshot.TopBar(MainTopFocusTarget.NavAction("gallery"))

    private var selectedAction by mutableStateOf("gallery")
    private var legacyAction by mutableStateOf<String?>(null)
    private var homeState by mutableStateOf<PhotoStreamUiState>(PhotoStreamUiState.Loading)
    private var collectionState by mutableStateOf<CollectionUiState?>(null)
    private var collectionRestoreFocusTarget by mutableStateOf<MainCollectionFocusTarget?>(null)
    private var homeRestoreFocusTarget by mutableStateOf<HomePhotoFocusTarget?>(null)
    private var heroState by mutableStateOf(HeroState())
    private var loadingMessage by mutableStateOf<String?>(null)
    private var appUpdateState by mutableStateOf<AppUpdateUiState>(AppUpdateUiState.Idle)
    private var topFocusRequestSerial by mutableStateOf(0)
    private var settingsFocusRequestSerial by mutableStateOf(0)
    private var pendingTopFocusTarget by mutableStateOf<MainTopFocusTarget>(MainTopFocusTarget.NavAction("gallery"))
    private var whiteThemeEnabled by mutableStateOf(false)
    private var currentProfileLabel by mutableStateOf("当前用户")
    private var profileSummaries by mutableStateOf<List<UserProfileStore.ProfileSummary>>(emptyList())
    private var profileSwitcherVisible by mutableStateOf(false)
    private var rememberBrowsePositionEnabled by mutableStateOf(false)
    private var isBackPressedOnce = false
    private var pageDepth = 0
    private var currentBrowsePosition: UserProfileStore.BrowsePosition? = null
    private var suppressBrowsePositionSave = false
    private var sessionChangeListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val appUpdateChecker by lazy { AppUpdateChecker(this) }
    private val appUpdateInstaller by lazy { AppUpdateInstaller(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        profileStore = UserProfileStore(this)
        profileStore.ensureActiveProfileFromCurrentSession()

        val prefs = getSharedPreferences("fn_photo_prefs", Context.MODE_PRIVATE)
        baseUrl = prefs.getString("nas_url", "").orEmpty()
        token = prefs.getString("api_token", "").orEmpty()
        secret = prefs.getString("secret", "").orEmpty()

        if (baseUrl.isBlank() || token.isBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        refreshProfileState()
        watchSessionChanges()

        val service = Retrofit.Builder()
            .baseUrl(baseUrl.trimEnd('/') + "/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(HttpClientProvider.getClient(this))
            .build()
            .create(FnHttpApi::class.java)
        api = service
        displayBrowseRepository = DisplayBrowseRepository(service, token)

        setContent {
            TvPhotoApp(
                selectedAction = selectedAction,
                legacyAction = legacyAction,
                navItems = buildNavItems(),
                homeState = homeState,
                collectionState = collectionState,
                collectionRestoreFocusTarget = collectionRestoreFocusTarget,
                heroState = heroState,
                loadingMessage = loadingMessage,
                token = token,
                serverUrl = baseUrl,
                currentProfileLabel = currentProfileLabel,
                profiles = profileSummaries,
                profileSwitcherVisible = profileSwitcherVisible,
                currentVersionText = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                appUpdateState = appUpdateState,
                topFocusRequestSerial = topFocusRequestSerial,
                settingsFocusRequestSerial = settingsFocusRequestSerial,
                topFocusTarget = pendingTopFocusTarget,
                whiteThemeEnabled = whiteThemeEnabled,
                rememberBrowsePositionEnabled = rememberBrowsePositionEnabled,
                onNavSelected = ::handleMenuSelection,
                onSettingsSelected = ::showSettingsFromTopBar,
                onThemeToggle = { whiteThemeEnabled = !whiteThemeEnabled },
                onUpdateAction = ::handleUpdateAction,
                onLogout = ::logout,
                onSwitchUser = ::showProfileSwitcher,
                onProfileSelected = ::switchToProfile,
                onAddProfile = ::addProfile,
                onDismissProfileSwitcher = { profileSwitcherVisible = false },
                onRememberBrowsePositionChanged = ::setRememberBrowsePosition,
                onPhotoSelected = ::openMediaDetail,
                onPhotoFocused = ::handleHomePhotoFocused,
                onLoadMore = ::loadMoreHomeStream,
                onCollectionSelected = ::handleCollectionSelection,
                onCollectionFocused = ::handleCollectionFocused,
                onCollectionRestoreFocusConsumed = { collectionRestoreFocusTarget = null },
                homeRestoreFocusTarget = homeRestoreFocusTarget,
                onHomeRestoreFocusConsumed = { homeRestoreFocusTarget = null },
                onTopBarFocusChanged = ::recordTopBarFocus,
                onContentFocused = ::recordContentFocus,
                activity = this,
            )
        }

        logAppVersionAndStats()
        checkForUpdates(force = false)
        if (!restoreRememberedBrowsePosition()) {
            resetToRoute(MainPageRoute.Gallery)
        }
    }

    private fun refreshProfileState() {
        profileStore.ensureActiveProfileFromCurrentSession()
        profileSummaries = profileStore.listProfiles()
        currentProfileLabel = profileStore.activeProfileTitle
        rememberBrowsePositionEnabled = profileStore.isRememberBrowsePositionEnabled
    }

    private fun watchSessionChanges() {
        val prefs = getSharedPreferences(SessionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
        sessionChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            when (key) {
                "api_token" -> {
                    val newToken = sharedPreferences.getString("api_token", "").orEmpty()
                    if (newToken.isNotBlank() && newToken != token) {
                        token = newToken
                        displayBrowseRepository = api?.let { DisplayBrowseRepository(it, newToken) }
                        displayBrowseLifecycle.onSessionTokenChanged()
                    }
                }
                "secret" -> secret = sharedPreferences.getString("secret", "").orEmpty()
            }
        }
        sessionChangeListener?.let { prefs.registerOnSharedPreferenceChangeListener(it) }
    }

    private fun setRememberBrowsePosition(enabled: Boolean) {
        profileStore.setRememberBrowsePositionEnabled(enabled)
        rememberBrowsePositionEnabled = enabled
        if (enabled) {
            persistBrowsePositionIfNeeded()
        }
    }

    private fun showProfileSwitcher() {
        refreshProfileState()
        profileSwitcherVisible = true
    }

    private fun switchToProfile(profile: UserProfileStore.ProfileSummary) {
        if (profile.isActive) {
            profileSwitcherVisible = false
            return
        }
        persistBrowsePositionIfNeeded()
        if (profileStore.switchToProfile(profile.id)) {
            suppressBrowsePositionSave = true
            profileSwitcherVisible = false
            Toast.makeText(this, "已切换到 ${profile.displayName}", Toast.LENGTH_SHORT).show()
            restartMainActivity()
        } else {
            Toast.makeText(this, "切换用户失败，请重新登录", Toast.LENGTH_LONG).show()
        }
    }

    private fun addProfile() {
        persistBrowsePositionIfNeeded()
        profileStore.persistCurrentSessionToActiveProfile()
        suppressBrowsePositionSave = true
        profileSwitcherVisible = false
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra(LoginActivity.EXTRA_ADD_PROFILE, true),
        )
        finish()
    }

    private fun restartMainActivity() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun restoreRememberedBrowsePosition(): Boolean {
        if (!profileStore.isRememberBrowsePositionEnabled) {
            return false
        }
        val position = profileStore.activeBrowsePosition ?: return false
        return openBrowsePosition(position)
    }

    private fun openBrowsePosition(position: UserProfileStore.BrowsePosition): Boolean {
        return when (position.kind) {
            "collection" -> {
                if (position.action.isBlank() || position.itemId.isBlank()) {
                    false
                } else {
                    val route = MainRouteResolver.topLevel(position.action) ?: return false
                    resetToRoute(
                        route,
                        MainFocusSnapshot.Collection(MainCollectionFocusTarget(position.itemType, position.itemId)),
                    )
                    true
                }
            }
            "photo" -> {
                if (position.itemId.isBlank()) {
                    false
                } else {
                    val focus = MainFocusSnapshot.Photo(HomePhotoFocusTarget(position.itemId, position.gridIndex))
                    when (position.sourceType) {
                        "recent" -> resetToRoute(MainPageRoute.Recent, focus)
                        "favorites" -> resetToRoute(MainPageRoute.Favorites, focus)
                        "album" -> resetToNestedRoute(
                            parentRoute = if (position.action == "shared") MainPageRoute.Shared else MainPageRoute.Albums,
                            parentFocus = MainFocusSnapshot.Collection(
                                MainCollectionFocusTarget(
                                    if (position.action == "shared") "shared_album" else "album",
                                    position.sourceId,
                                ),
                            ),
                            route = MainPageRoute.AlbumPhotos(
                                position.sourceId.toIntOrNull() ?: return false,
                                position.sourceTitle.ifBlank { "相册" },
                                position.action.ifBlank { "albums" },
                            ),
                            focus = focus,
                        )
                        "folder" -> resetToNestedRoute(
                            parentRoute = MainPageRoute.Folders,
                            parentFocus = null,
                            route = MainPageRoute.DirectPhotos(
                                StreamSource.folder(position.sourcePayload, position.sourceTitle.ifBlank { "文件夹" }),
                            ),
                            focus = focus,
                        )
                        "person" -> resetToNestedRoute(
                            parentRoute = MainPageRoute.People,
                            parentFocus = MainFocusSnapshot.Collection(
                                MainCollectionFocusTarget("person", position.sourceId),
                            ),
                            route = MainPageRoute.DirectPhotos(
                                StreamSource.person(
                                    position.sourceId.toIntOrNull() ?: return false,
                                    position.sourceTitle.ifBlank { "人物" },
                                ),
                            ),
                            focus = focus,
                        )
                        "place" -> resetToNestedRoute(
                            parentRoute = MainPageRoute.Places,
                            parentFocus = MainFocusSnapshot.Collection(
                                MainCollectionFocusTarget("place", "${position.sourceId}:${position.sourcePayload}"),
                            ),
                            route = MainPageRoute.DirectPhotos(
                                StreamSource.place(
                                    position.sourceId,
                                    position.sourcePayload,
                                    position.sourceTitle.ifBlank { "地点" },
                                ),
                            ),
                            focus = focus,
                        )
                        "smart" -> resetToNestedRoute(
                            parentRoute = MainPageRoute.Smart,
                            parentFocus = MainFocusSnapshot.Collection(
                                MainCollectionFocusTarget(
                                    "smart_category",
                                    position.sourcePayload.ifBlank { position.sourceTitle },
                                ),
                            ),
                            route = MainPageRoute.DirectPhotos(
                                StreamSource.smartCategory(
                                    position.sourcePayload.ifBlank { position.sourceTitle },
                                    position.sourceTitle.ifBlank { "智能分类" },
                                ),
                            ),
                            focus = focus,
                        )
                        "tag" -> {
                            val restoration = DisplayBrowseRestorationResolver.forTag(
                                rawName = position.sourceId,
                                displayTitle = position.sourceTitle,
                            ) ?: return false
                            resetToNestedRoute(
                                parentRoute = restoration.parentRoute,
                                parentFocus = MainFocusSnapshot.Collection(
                                    restoration.parentFocusTarget,
                                ),
                                route = restoration.route,
                                focus = focus,
                            )
                        }
                        "media_type" -> {
                            val categoryCode = position.sourceId.toIntOrNull() ?: return false
                            val restoration = DisplayBrowseRestorationResolver.forMediaType(
                                categoryCode = categoryCode,
                                fileType = position.sourcePayload,
                                displayTitle = position.sourceTitle,
                            ) ?: return false
                            resetToNestedRoute(
                                parentRoute = restoration.parentRoute,
                                parentFocus = MainFocusSnapshot.Collection(
                                    restoration.parentFocusTarget,
                                ),
                                route = restoration.route,
                                focus = focus,
                            )
                        }
                        else -> resetToRoute(MainPageRoute.Gallery, focus)
                    }
                    true
                }
            }
            else -> false
        }
    }

    private fun buildNavItems(): List<TvNavItem> {
        return TvNavigation.homeStreamTopItems()
    }

    private fun handleMenuSelection(item: TvNavItem) {
        if (!item.enabled) {
            Toast.makeText(this, "${item.title} 暂不可用", Toast.LENGTH_SHORT).show()
            return
        }

        clearCollectionFocusTargets()
        when (val route = MainRouteResolver.topLevel(item.action)) {
            null -> when (item.action) {
                "logout" -> logout()
                else -> Toast.makeText(this, "图库照片流先行", Toast.LENGTH_SHORT).show()
            }
            else -> navigateTo(route)
        }
    }

    private fun navigateTo(route: MainPageRoute) {
        pageBackStack.pushCurrent(
            currentRoute = currentRoute,
            focus = currentFocusSnapshot,
            nextRoute = route,
        )
        openRoute(route, focus = null)
    }

    private fun resetToRoute(route: MainPageRoute, focus: MainFocusSnapshot? = null) {
        pageBackStack.resetTo(route)
        openRoute(route, focus)
    }

    private fun resetToNestedRoute(
        parentRoute: MainPageRoute,
        parentFocus: MainFocusSnapshot?,
        route: MainPageRoute,
        focus: MainFocusSnapshot,
    ) {
        pageBackStack.resetTo(parentRoute)
        pageBackStack.pushCurrent(parentRoute, parentFocus, route)
        openRoute(route, focus)
    }

    private fun goBackInPageStack(): Boolean {
        val previous = pageBackStack.popPrevious() ?: return false
        openRoute(previous.route, previous.focus)
        return true
    }

    private fun openRoute(route: MainPageRoute, focus: MainFocusSnapshot?) {
        loadPublicationGate.invalidate()
        displayBrowseLifecycle.invalidate()
        currentDisplayBrowseOwner = null
        currentDirectLoadOwner = null
        currentRoute = route
        currentFocusSnapshot = focus
        val photoFocus = (focus as? MainFocusSnapshot.Photo)?.target
        val collectionFocus = (focus as? MainFocusSnapshot.Collection)?.target
        val settingsContentFocus = focus == MainFocusSnapshot.SettingsContent
        if (collectionFocus != null) {
            collectionRestoreFocusTarget = collectionFocus
        }
        val topFocus = focus as? MainFocusSnapshot.TopBar
        if (topFocus != null) {
            pendingTopFocusTarget = topFocus.target
        }

        when (route) {
            MainPageRoute.Gallery -> loadHomeStream(photoFocus)
            MainPageRoute.Recent -> loadTimelinePhotoStream(StreamSource.recent(), photoFocus)
            MainPageRoute.Favorites -> loadTimelinePhotoStream(StreamSource.favorites(), photoFocus)
            MainPageRoute.Albums -> loadAlbumGrid()
            MainPageRoute.Folders -> loadFolderGrid()
            MainPageRoute.People -> loadPeopleGrid()
            MainPageRoute.Places -> loadPlaceGrid()
            MainPageRoute.Shared -> loadSharedGrid()
            MainPageRoute.Smart -> loadSmartCategoryGrid()
            MainPageRoute.Tags -> loadTagGrid()
            MainPageRoute.MediaTypes -> loadMediaTypeGrid()
            MainPageRoute.Settings -> showSettingsPage()
            is MainPageRoute.AlbumPhotos -> loadAlbumPhotoStream(
                route.albumId,
                route.title,
                route.ownerAction,
                photoFocus,
            )
            is MainPageRoute.FolderBrowser -> loadFolderBrowserGrid(route.folderPath, route.title)
            is MainPageRoute.DirectPhotos -> when (route.source) {
                is StreamSource.Person -> loadTimelinePhotoStream(route.source, photoFocus)
                else -> loadDirectPhotoStream(route.source, photoFocus)
            }
        }

        if (topFocus != null) {
            requestSelectedTopBarFocus(topFocus.target)
        } else if (settingsContentFocus) {
            requestSettingsContentFocus()
        }
    }

    private fun showSettingsFromTopBar() {
        if (currentRoute == MainPageRoute.Settings) {
            requestSettingsContentFocus()
            return
        }
        currentFocusSnapshot = MainFocusSnapshot.TopBar(MainTopFocusTarget.Settings)
        navigateTo(MainPageRoute.Settings)
    }

    private fun recordTopBarFocus(target: MainTopFocusTarget, focused: Boolean) {
        if (
            focused &&
            MainFocusRestorePolicy.shouldRecordTopBarFocus(
                collectionRestorePending = collectionRestoreFocusTarget != null,
                photoRestorePending = homeRestoreFocusTarget != null,
            )
        ) {
            currentFocusSnapshot = MainFocusSnapshot.TopBar(target)
        }
    }

    private fun recordContentFocus() {
        if (currentRoute == MainPageRoute.Settings) {
            currentFocusSnapshot = MainFocusSnapshot.SettingsContent
        }
    }

    private fun loadHomeStream(restoreFocusTarget: HomePhotoFocusTarget? = null) {
        legacyAction = null
        selectedAction = "gallery"
        lastLegacyAction = null
        pageDepth = 0
        removeLegacyFragment()
        clearHero()

        homeLoadJob?.cancel()
        homePageJob?.cancel()
        collectionJob?.cancel()
        collectionState = null
        currentStreamSource = StreamSource.gallery()
        resetGalleryPaging()
        currentHomePhotos = emptyList()
        homeRestoreFocusTarget = restoreFocusTarget
        homeState = PhotoStreamUiState.Loading
        homeLoadJob = activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    galleryTimelineItems = fetchGalleryTimeline()
                    galleryHasMore = galleryTimelineItems.isNotEmpty()
                    fetchInitialGalleryPhotos(restoreFocusTarget)
                }
            }

            result
                .onSuccess { photos ->
                    currentHomePhotos = photos
                    updateHomeHero(currentHomePhotos.firstOrNull())
                    homeState = if (currentHomePhotos.isEmpty()) {
                        PhotoStreamUiState.Empty
                    } else {
                        PhotoStreamUiState.Content(
                            title = currentStreamSource.title,
                            groups = PhotoStreamGrouper.groupByDay(currentHomePhotos),
                            loadingMore = false,
                            hasMore = galleryHasMore,
                        )
                    }
                }
                .onFailure { throwable ->
                    Log.e(TAG, "Load Compose home photo stream failed", throwable)
                    currentHomePhotos = emptyList()
                    homeState = PhotoStreamUiState.Error("图库加载失败")
                }
        }
    }

    private fun resetGalleryPaging() {
        galleryTimelineItems = emptyList()
        galleryTimelineCursor = 0
        galleryDateOffset = 0
        directPhotoOffset = 0
        galleryHasMore = false
        displayBrowseLifecycle.invalidate()
        currentDisplayBrowseOwner = null
    }

    private fun fetchGalleryTimeline(): List<TimelineBucket> {
        val service = api ?: return emptyList()
        when (val source = currentStreamSource) {
            is StreamSource.Recent -> {
                val recentAuth = FnAuthUtils.generateAuthX("/p/api/v1/explore/recent_timeline", "GET", null)
                val recentResponse = service.getRecentTimeline(token, recentAuth).execute()
                if (!recentResponse.isSuccessful) {
                    throw IOException("explore/recent_timeline HTTP ${recentResponse.code()}")
                }
                val body = recentResponse.body() ?: return emptyList()
                if (body.code != 0) {
                    throw IOException("explore/recent_timeline code ${body.code}")
                }
                return body.data?.list.orEmpty().map {
                    TimelineBucket(it.year, it.month, it.day, it.itemCount)
                }
            }
            is StreamSource.Person -> {
                val params = "id=${source.personId}"
                val authx = FnAuthUtils.generateAuthX("/p/api/v1/ai-person/photoLibrary/timeLine", "GET", params)
                val response = service.getPersonTimeline(token, authx, source.personId).execute()
                if (!response.isSuccessful) {
                    throw IOException("ai-person/photoLibrary/timeLine HTTP ${response.code()}")
                }
                val body = response.body() ?: return emptyList()
                if (body.code != 0) {
                    throw IOException("ai-person/photoLibrary/timeLine code ${body.code}")
                }
                return body.data?.list.orEmpty().map {
                    TimelineBucket(it.year, it.month, it.day, it.itemCount)
                }
            }
            else -> Unit
        }

        val isCollect = currentTimelineCollectFilter()
        val params = isCollect?.let { "is_collect=$it" }
        val timelineAuth = FnAuthUtils.generateAuthX("/p/api/v1/gallery/timeline", "GET", params)
        val timelineResponse = service.getTimeline(token, timelineAuth, isCollect).execute()
        if (!timelineResponse.isSuccessful) {
            throw IOException("gallery/timeline HTTP ${timelineResponse.code()}")
        }

        val body = timelineResponse.body() ?: return emptyList()
        if (body.code != 0) {
            throw IOException("gallery/timeline code ${body.code}")
        }

        return body.data?.list.orEmpty().map {
            TimelineBucket(it.year, it.month, it.day, it.itemCount)
        }
    }

    private fun loadTimelinePhotoStream(source: StreamSource, restoreFocusTarget: HomePhotoFocusTarget? = null) {
        legacyAction = null
        selectedAction = source.action
        lastLegacyAction = null
        pageDepth = if (source is StreamSource.Person) 1 else 0
        removeLegacyFragment()
        clearHero()

        homeLoadJob?.cancel()
        homePageJob?.cancel()
        collectionJob?.cancel()
        collectionState = null
        currentStreamSource = source
        resetGalleryPaging()
        currentHomePhotos = emptyList()
        homeRestoreFocusTarget = restoreFocusTarget
        homeState = PhotoStreamUiState.Loading

        homeLoadJob = activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    galleryTimelineItems = fetchGalleryTimeline()
                    galleryHasMore = galleryTimelineItems.isNotEmpty()
                    fetchInitialGalleryPhotos(restoreFocusTarget)
                }
            }

            result
                .onSuccess { photos ->
                    currentHomePhotos = photos
                    updateHomeHero(currentHomePhotos.firstOrNull())
                    homeState = if (currentHomePhotos.isEmpty()) {
                        PhotoStreamUiState.Empty
                    } else {
                        PhotoStreamUiState.Content(
                            title = source.title,
                            groups = PhotoStreamGrouper.groupByDay(currentHomePhotos),
                            loadingMore = false,
                            hasMore = galleryHasMore,
                        )
                    }
                }
                .onFailure { throwable ->
                    Log.e(TAG, "Load ${source.action} timeline photo stream failed", throwable)
                    currentHomePhotos = emptyList()
                    homeState = PhotoStreamUiState.Error("${source.title}加载失败")
                }
        }
    }

    private fun fetchInitialGalleryPhotos(restoreFocusTarget: HomePhotoFocusTarget?): List<PhotoStreamPhoto> {
        val photos = fetchNextGalleryPhotos(INITIAL_PHOTO_LIMIT).toMutableList()
        fetchUntilRestoreTarget(photos, restoreFocusTarget) {
            fetchNextGalleryPhotos(NEXT_PAGE_PHOTO_LIMIT)
        }
        return photos
    }

    private fun fetchNextGalleryPhotos(pageLimit: Int): List<PhotoStreamPhoto> {
        val service = api ?: return emptyList()
        val photos = mutableListOf<PhotoStreamPhoto>()

        while (photos.size < pageLimit && galleryTimelineCursor < galleryTimelineItems.size) {
            val item = galleryTimelineItems[galleryTimelineCursor]
            if (item.itemCount <= 0) {
                moveToNextGalleryDate()
                continue
            }

            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", item.year, item.month, item.day)
            val dateTime = dateStr.replace("-", ":")
            val startTime = "$dateTime 00:00:00"
            val endTime = "$dateTime 23:59:59"
            val remainingInDate = item.itemCount - galleryDateOffset
            if (remainingInDate <= 0) {
                moveToNextGalleryDate()
                continue
            }

            val limit = minOf(remainingInDate, pageLimit - photos.size, PER_DATE_FETCH_LIMIT)
            val offset = galleryDateOffset
            val mode = currentTimelineMode()
            val isCollect = currentTimelineCollectFilter()
            val params = buildString {
                append("end_time=$endTime&limit=$limit&mode=$mode&offset=$offset&start_time=$startTime")
                if (isCollect != null) {
                    append("&is_collect=$isCollect")
                }
            }
            val personSource = currentStreamSource as? StreamSource.Person
            val loadedPhotos = if (personSource != null) {
                fetchPersonPhotosForDate(personSource.personId, startTime, endTime, limit, offset)
            } else {
                val authx = FnAuthUtils.generateAuthX("/p/api/v1/gallery/getList", "GET", params)
                val photoResponse = service
                    .getPhotosByTimeRange(token, authx, startTime, endTime, limit, offset, mode, isCollect)
                    .execute()
                if (!photoResponse.isSuccessful) {
                    Log.w(TAG, "gallery/getList failed for $dateStr: ${photoResponse.code()}")
                    null
                } else {
                    photoResponse.body()?.data?.list.orEmpty()
                }
            }

            if (loadedPhotos != null) {
                loadedPhotos.mapTo(photos) { PhotoStreamMapper.fromGalleryPhoto(it, baseUrl) }
                if (loadedPhotos.isEmpty()) {
                    moveToNextGalleryDate()
                } else {
                    galleryDateOffset += maxOf(loadedPhotos.size, limit)
                    if (galleryDateOffset >= item.itemCount) {
                        moveToNextGalleryDate()
                    }
                }
            } else {
                moveToNextGalleryDate()
            }
        }

        galleryHasMore = galleryTimelineCursor < galleryTimelineItems.size
        return photos
    }

    private fun fetchPersonPhotosForDate(
        personId: Int,
        startTime: String,
        endTime: String,
        limit: Int,
        offset: Int,
    ): List<FnHttpApi.GalleryPhoto> {
        val service = api ?: return emptyList()
        val params = "album_id=$personId" +
            "&endTime=$endTime" +
            "&end_time=$endTime" +
            "&limit=$limit" +
            "&offset=$offset" +
            "&personId=$personId" +
            "&startTime=$startTime" +
            "&start_time=$startTime"
        val authx = FnAuthUtils.generateAuthX("/p/api/v1/ai-person/photoLibrary/list", "GET", params)
        val response = service
            .getPersonPhotos(token, authx, personId, startTime, endTime, limit, offset, personId, startTime, endTime)
            .execute()
        if (!response.isSuccessful) {
            throw IOException("ai-person/photoLibrary/list HTTP ${response.code()}")
        }
        return response.body()?.data?.list.orEmpty()
    }

    private fun currentTimelineCollectFilter(): Int? {
        return if (currentStreamSource is StreamSource.Favorites) 1 else null
    }

    private fun currentTimelineMode(): String {
        return when (currentStreamSource) {
            is StreamSource.Favorites -> "datetime"
            is StreamSource.Recent -> "createdAt"
            else -> "index"
        }
    }

    private fun loadDirectPhotoStream(source: StreamSource, restoreFocusTarget: HomePhotoFocusTarget? = null) {
        legacyAction = null
        selectedAction = source.action
        lastLegacyAction = null
        pageDepth = 1
        removeLegacyFragment()
        clearHero()

        homeLoadJob?.cancel()
        homePageJob?.cancel()
        collectionJob?.cancel()
        collectionState = null
        currentStreamSource = source
        resetGalleryPaging()
        val displayOwner = source.displayCategorySource()?.let { displaySource ->
            displayBrowseLifecycle.begin(
                loadIdentity = "direct:${source.displayLoadIdentity()}",
                source = displaySource,
            )
        }
        val publicationOwner = if (displayOwner == null) {
            loadPublicationGate.begin("direct:${source.displayLoadIdentity()}")
        } else {
            null
        }
        currentDirectLoadOwner = publicationOwner
        currentDisplayBrowseOwner = displayOwner
        currentHomePhotos = emptyList()
        homeRestoreFocusTarget = restoreFocusTarget
        homeState = PhotoStreamUiState.Loading

        homeLoadJob = activityScope.launch {
            val result = try {
                val load = withContext(Dispatchers.IO) {
                    if (displayOwner == null) {
                        val photos = fetchInitialDirectPhotos(source, restoreFocusTarget)
                        DirectPhotoLoadResult(photos, directPhotoOffset, galleryHasMore)
                    } else {
                        fetchInitialDisplayBrowsePhotos(displayOwner, restoreFocusTarget)
                    }
                } ?: return@launch
                Result.success(load)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }

            result
                .onSuccess { load ->
                    val publish = {
                        directPhotoOffset = load.offset
                        galleryHasMore = load.hasMore
                        currentHomePhotos = load.photos
                        if (
                            MainFocusRestorePolicy.shouldRequestInitialPhotoFocus(
                                restorePending = homeRestoreFocusTarget != null,
                                hasPhotos = currentHomePhotos.isNotEmpty(),
                            )
                        ) {
                            homeRestoreFocusTarget = HomePhotoFocusTarget(
                                photoId = currentHomePhotos.first().id,
                                itemIndex = 0,
                            )
                        }
                        updateHomeHero(currentHomePhotos.firstOrNull())
                        homeState = if (currentHomePhotos.isEmpty()) {
                            PhotoStreamUiState.Empty
                        } else {
                            PhotoStreamUiState.Content(
                                title = source.title,
                                groups = PhotoStreamGrouper.groupByDay(currentHomePhotos),
                                loadingMore = false,
                                hasMore = load.hasMore,
                            )
                        }
                    }
                    if (displayOwner == null) {
                        publicationOwner?.let { loadPublicationGate.publish(it, publish) }
                    } else {
                        displayBrowseLifecycle.publish(displayOwner, publish)
                    }
                }
                .onFailure { throwable ->
                    val publishFailure = {
                        Log.e(TAG, "Load ${source.action} photo stream failed", throwable)
                        currentHomePhotos = emptyList()
                        homeState = PhotoStreamUiState.Error("${source.title}加载失败")
                    }
                    if (displayOwner == null) {
                        publicationOwner?.let { loadPublicationGate.publish(it, publishFailure) }
                    } else {
                        displayBrowseLifecycle.publish(displayOwner, publishFailure)
                    }
                }
        }
    }

    private fun loadAlbumPhotoStream(
        albumId: Int,
        title: String,
        ownerAction: String = "albums",
        restoreFocusTarget: HomePhotoFocusTarget? = null,
    ) {
        legacyAction = null
        selectedAction = ownerAction
        pageDepth = 1
        removeLegacyFragment()
        clearHero()

        homeLoadJob?.cancel()
        homePageJob?.cancel()
        collectionJob?.cancel()
        collectionState = null
        val source = StreamSource.album(albumId, title)
        currentStreamSource = source
        resetGalleryPaging()
        currentHomePhotos = emptyList()
        homeRestoreFocusTarget = restoreFocusTarget
        homeState = PhotoStreamUiState.Loading

        homeLoadJob = activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    fetchInitialDirectPhotos(source, restoreFocusTarget)
                }
            }

            result
                .onSuccess { photos ->
                    currentHomePhotos = photos
                    updateHomeHero(currentHomePhotos.firstOrNull())
                    homeState = if (currentHomePhotos.isEmpty()) {
                        PhotoStreamUiState.Empty
                    } else {
                        PhotoStreamUiState.Content(
                            title = title,
                            groups = PhotoStreamGrouper.groupByDay(currentHomePhotos),
                            loadingMore = false,
                            hasMore = galleryHasMore,
                        )
                    }
                }
                .onFailure { throwable ->
                    Log.e(TAG, "Load album photos failed", throwable)
                    currentHomePhotos = emptyList()
                    homeState = PhotoStreamUiState.Error("相册加载失败")
                }
        }
    }

    private fun fetchInitialDirectPhotos(
        source: StreamSource,
        restoreFocusTarget: HomePhotoFocusTarget?,
    ): List<PhotoStreamPhoto> {
        val photos = fetchNextDirectPhotos(source, INITIAL_PHOTO_LIMIT).toMutableList()
        fetchUntilRestoreTarget(photos, restoreFocusTarget) {
            fetchNextDirectPhotos(source, NEXT_PAGE_PHOTO_LIMIT)
        }
        return photos
    }

    private fun fetchUntilRestoreTarget(
        photos: MutableList<PhotoStreamPhoto>,
        restoreFocusTarget: HomePhotoFocusTarget?,
        fetchMore: () -> List<PhotoStreamPhoto>,
    ) {
        val targetId = restoreFocusTarget?.photoId?.takeIf { it.isNotBlank() } ?: return
        while (
            photos.none { it.id == targetId } &&
            galleryHasMore &&
            photos.size < RESTORE_PHOTO_SCAN_LIMIT
        ) {
            val more = fetchMore()
            if (more.isEmpty()) {
                break
            }
            photos.addAll(more)
        }
    }

    private suspend fun fetchInitialDisplayBrowsePhotos(
        owner: DisplayBrowseLifecycle.Owner,
        restoreFocusTarget: HomePhotoFocusTarget?,
    ): DirectPhotoLoadResult? {
        var page = fetchDisplayBrowsePhotos(owner, INITIAL_PHOTO_LIMIT) ?: return null
        val photos = page.photos
            .map { PhotoStreamMapper.fromDisplayBrowsePhoto(it, baseUrl) }
            .toMutableList()
        val targetId = restoreFocusTarget?.photoId?.takeIf { it.isNotBlank() }
        while (
            targetId != null &&
            photos.none { it.id == targetId } &&
            page.hasMore &&
            photos.size < RESTORE_PHOTO_SCAN_LIMIT
        ) {
            page = fetchDisplayBrowsePhotos(owner, NEXT_PAGE_PHOTO_LIMIT) ?: return null
            val more = page.photos.map { PhotoStreamMapper.fromDisplayBrowsePhoto(it, baseUrl) }
            if (more.isEmpty()) {
                break
            }
            photos.addAll(more)
        }
        return DirectPhotoLoadResult(
            photos = photos,
            offset = page.offset,
            hasMore = page.hasMore,
        )
    }

    private fun fetchNextDirectPhotos(source: StreamSource, limit: Int): List<PhotoStreamPhoto> {
        val service = api ?: return emptyList()
        val offset = directPhotoOffset
        (source as? StreamSource.Folder)?.let { folderSource ->
            return fetchNextFolderPhotos(folderSource, limit, offset)
        }

        val response = when (source) {
            is StreamSource.Recent -> {
                val params = "limit=$limit&offset=$offset"
                val authx = FnAuthUtils.generateAuthX("/p/api/v1/gallery/recent", "GET", params)
                service.getRecentPhotos(token, authx, limit, offset).execute()
            }
            is StreamSource.Favorites -> {
                val params = "limit=$limit&offset=$offset"
                val authx = FnAuthUtils.generateAuthX("/p/api/v1/photo/collect/list", "GET", params)
                service.getCollectList(token, authx, limit, offset).execute()
            }
            is StreamSource.Album -> {
                val params = "album_id=${source.albumId}&sort_by=date_time&sort_direction=desc&offset=$offset&limit=$limit"
                val authx = FnAuthUtils.generateAuthX("/p/api/v1/album/photos", "GET", params)
                service.getAlbumPhotos(token, authx, source.albumId, "date_time", "desc", offset, limit).execute()
            }
            is StreamSource.Place -> return fetchPlacePhotos(source)
            is StreamSource.SmartCategory -> return fetchSmartCategoryPhotos(source)
            is StreamSource.Tag,
            is StreamSource.MediaType,
            -> throw IllegalStateException("Display sources use generation-bound paging")
            is StreamSource.Person -> throw IllegalStateException("Person uses timeline paging")
            is StreamSource.Folder -> throw IllegalStateException("Folder uses folder paging")
            StreamSource.Gallery -> throw IllegalStateException("Gallery uses timeline paging")
        }

        if (!response.isSuccessful) {
            throw IOException("${source.action} HTTP ${response.code()}")
        }

        val data = response.body()?.data
        val photos = data?.list.orEmpty()
        directPhotoOffset += photos.size
        galleryHasMore = data?.hasNext == true || photos.size >= limit
        return photos.map { PhotoStreamMapper.fromGalleryPhoto(it, baseUrl) }
    }

    private suspend fun fetchDisplayBrowsePhotos(
        owner: DisplayBrowseLifecycle.Owner,
        limit: Int,
    ): DisplayBrowseLoadState.Page? {
        val repository = displayBrowseRepository
            ?: return DisplayBrowseLoadState.Page(emptyList(), offset = 0, hasMore = false)
        return displayBrowseLifecycle.loadNext(owner, limit) {
            repository.openPhotoPager(owner.source)
        }
    }

    private fun fetchPlacePhotos(source: StreamSource.Place): List<PhotoStreamPhoto> {
        val service = api ?: return emptyList()
        if (directPhotoOffset > 0) {
            galleryHasMore = false
            return emptyList()
        }

        val requestJson = gson.toJson(FnHttpApi.GeoSearchRequest(source.country, source.city))
        val authx = FnAuthUtils.generateAuthX("/p/api/v2/search/results", "POST", requestJson)
        val requestBody = requestJson.toRequestBody(JSON_MEDIA_TYPE)
        val response = service.searchGeoPhotos(token, authx, requestBody).execute()
        if (!response.isSuccessful) {
            throw IOException("search/results HTTP ${response.code()}")
        }

        val body = response.body()
        if (body?.code != 0) {
            throw IOException("search/results code ${body?.code}: ${body?.msg}")
        }

        val photos = body.data?.list.orEmpty()
        directPhotoOffset += photos.size
        galleryHasMore = false
        return photos.map { PhotoStreamMapper.fromGalleryPhoto(it, baseUrl) }
    }

    private fun fetchSmartCategoryPhotos(source: StreamSource.SmartCategory): List<PhotoStreamPhoto> {
        val service = api ?: return emptyList()
        if (directPhotoOffset > 0) {
            galleryHasMore = false
            return emptyList()
        }

        val requestJson = gson.toJson(FnHttpApi.MagicSearchRequest(source.keyword))
        val authx = FnAuthUtils.generateAuthX("/p/api/v1/magic-search/do", "POST", requestJson)
        val requestBody = requestJson.toRequestBody(JSON_MEDIA_TYPE)
        val response = service.magicSearch(token, authx, requestBody).execute()
        if (!response.isSuccessful) {
            throw IOException("magic-search/do HTTP ${response.code()}")
        }

        val body = response.body()
        if (body?.code != 0) {
            throw IOException("magic-search/do code ${body?.code}: ${body?.msg}")
        }

        val photos = body.data?.list.orEmpty()
        directPhotoOffset += photos.size
        galleryHasMore = false
        return photos.map { PhotoStreamMapper.fromGalleryPhoto(it, baseUrl) }
    }

    private fun fetchNextFolderPhotos(
        source: StreamSource.Folder,
        limit: Int,
        offset: Int,
    ): List<PhotoStreamPhoto> {
        val service = api ?: return emptyList()
        val params = "desc=false&folderPath=${source.folderPath}&limit=$limit&offset=$offset&orderBy=2"
        val authx = FnAuthUtils.generateAuthX("/p/api/v1/folder_view/getFileList", "GET", params)
        val response = service
            .getFolderFiles(token, authx, source.folderPath, false, 2, limit, offset)
            .execute()
        if (!response.isSuccessful) {
            throw IOException("folder_view/getFileList HTTP ${response.code()}")
        }

        val photos = response.body()?.data?.list.orEmpty()
        directPhotoOffset += photos.size
        galleryHasMore = photos.size >= limit
        return photos.map { PhotoStreamMapper.fromFolderMediaItem(it, baseUrl) }
    }

    private fun moveToNextGalleryDate() {
        galleryTimelineCursor++
        galleryDateOffset = 0
    }

    private fun loadMoreHomeStream() {
        val current = homeState as? PhotoStreamUiState.Content ?: return
        if (current.loadingMore || !current.hasMore) {
            return
        }

        val source = currentStreamSource
        val displayOwner = currentDisplayBrowseOwner
        val publicationOwner = currentDirectLoadOwner
        homeState = current.copy(loadingMore = true, loadMoreError = null)
        homePageJob?.cancel()
        homePageJob = activityScope.launch {
            val result = try {
                val load = withContext(Dispatchers.IO) {
                    if (displayOwner != null) {
                        val page = fetchDisplayBrowsePhotos(displayOwner, NEXT_PAGE_PHOTO_LIMIT)
                            ?: return@withContext null
                        DirectPhotoLoadResult(
                            photos = page.photos.map { PhotoStreamMapper.fromDisplayBrowsePhoto(it, baseUrl) },
                            offset = page.offset,
                            hasMore = page.hasMore,
                        )
                    } else {
                        val photos = if (source == StreamSource.Gallery ||
                            source is StreamSource.Favorites ||
                            source is StreamSource.Recent ||
                            source is StreamSource.Person
                        ) {
                            fetchNextGalleryPhotos(NEXT_PAGE_PHOTO_LIMIT)
                        } else {
                            fetchNextDirectPhotos(source, NEXT_PAGE_PHOTO_LIMIT)
                        }
                        DirectPhotoLoadResult(photos, directPhotoOffset, galleryHasMore)
                    }
                } ?: return@launch
                Result.success(load)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }

            result
                .onSuccess { load ->
                    val publish = {
                        directPhotoOffset = load.offset
                        galleryHasMore = load.hasMore
                        val updatedGroups = if (load.photos.isNotEmpty()) {
                            PhotoStreamGrouper.appendByDay(current.groups, load.photos)
                        } else {
                            current.groups
                        }
                        if (load.photos.isNotEmpty()) {
                            currentHomePhotos = currentHomePhotos + load.photos
                        }
                        homeState = PhotoStreamUiState.Content(
                            title = source.title,
                            groups = updatedGroups,
                            loadingMore = false,
                            hasMore = load.hasMore,
                        )
                    }
                    if (displayOwner != null) {
                        displayBrowseLifecycle.publish(displayOwner, publish)
                    } else {
                        publicationOwner?.let { loadPublicationGate.publish(it, publish) }
                    }
                }
                .onFailure { throwable ->
                    val publishFailure = {
                        Log.e(TAG, "Load more gallery photos failed", throwable)
                        val latest = homeState as? PhotoStreamUiState.Content ?: current
                        homeState = latest.copy(loadingMore = false, loadMoreError = "加载更多失败")
                    }
                    if (displayOwner != null) {
                        displayBrowseLifecycle.publish(displayOwner, publishFailure)
                    } else {
                        publicationOwner?.let { loadPublicationGate.publish(it, publishFailure) }
                    }
                }
        }
    }

    private fun loadAlbumGrid() {
        loadCollectionGrid("albums", "相册") {
            val service = api ?: return@loadCollectionGrid emptyList()
            val params = "sort_direction=desc&sort_by=date_time&offset=0&limit=1000"
            val authx = FnAuthUtils.generateAuthX("/p/api/v1/album/list", "GET", params)
            val response = service.getAlbums(token, authx, "desc", "date_time", 0, 1000).execute()
            if (!response.isSuccessful) {
                throw IOException("album/list HTTP ${response.code()}")
            }
            response.body()?.data?.list.orEmpty().map { album ->
                CollectionItem(
                    id = album.albumId.toString(),
                    title = album.albumName.orEmpty().ifBlank { "未命名相册" },
                    subtitle = mediaCountText(album.photoCount, album.videoCount),
                    type = "album",
                    imageUrl = absoluteNullable(album.posterUrl ?: album.posterImgUrl),
                    sourceId = album.albumId,
                )
            }
        }
    }

    private fun loadFolderGrid() {
        loadCollectionGrid("folders", "文件夹") {
            val service = api ?: return@loadCollectionGrid emptyList()
            val params = "desc=false&orderBy=2"
            val authx = FnAuthUtils.generateAuthX("/p/api/v1/photo/folder/list", "GET", params)
            val response = service.getManagedFolders(token, authx, false, 2).execute()
            if (!response.isSuccessful) {
                throw IOException("photo/folder/list HTTP ${response.code()}")
            }
            response.body()?.data?.list.orEmpty().map { folder ->
                CollectionItem(
                    id = folder.folderId.toString(),
                    title = folder.getFolderName(),
                    subtitle = mediaCountText(folder.photoCount, folder.videoCount),
                    type = "folder",
                    imageUrl = null,
                    sourceId = folder.folderId,
                    payload = folder.folderPath,
                )
            }
        }
    }

    private fun loadPeopleGrid() {
        loadCollectionGrid("people", "人物") {
            val service = api ?: return@loadCollectionGrid emptyList()
            val params = "getAll=true&limit=-1&orderBy=0"
            val authx = FnAuthUtils.generateAuthX("/p/api/v1/ai-person/list", "GET", params)
            val response = service.getPersonList(token, authx, true, -1, 0).execute()
            if (!response.isSuccessful) {
                throw IOException("ai-person/list HTTP ${response.code()}")
            }
            response.body()?.data?.list.orEmpty()
                .filter { !it.isHide }
                .map { person ->
                    CollectionItem(
                        id = person.id.toString(),
                        title = person.name?.takeIf { it.isNotBlank() } ?: "未命名人物",
                        subtitle = countText(person.itemCount),
                        type = "person",
                        imageUrl = if (person.faceId > 0) absoluteUrl("/p/api/v1/stream/face/${person.faceId}") else null,
                        sourceId = person.id,
                    )
                }
        }
    }

    private fun loadPlaceGrid() {
        loadCollectionGrid("places", "地点") {
            val service = api ?: return@loadCollectionGrid emptyList()
            val params = "offset=0&limit=-1"
            val authx = FnAuthUtils.generateAuthX("/p/api/v1/explore/geos", "GET", params)
            val response = service.getGeos(token, authx, 0, -1).execute()
            if (!response.isSuccessful) {
                throw IOException("explore/geos HTTP ${response.code()}")
            }
            response.body()?.data?.list.orEmpty().map { place ->
                val country = place.country.orEmpty()
                val city = place.city.orEmpty()
                val name = listOf(city, country)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { "未知地点" }
                CollectionItem(
                    id = "$country:$city",
                    title = name,
                    subtitle = countText(place.itemCount),
                    type = "place",
                    imageUrl = absoluteNullable(place.posterUrl),
                    sourceId = 0,
                    payload = country,
                    secondaryPayload = city,
                )
            }
        }
    }

    private fun loadSharedGrid() {
        loadCollectionGrid("shared", "共享") {
            val service = api ?: return@loadCollectionGrid emptyList()
            val params = "limit=1000&offset=0"
            val authx = FnAuthUtils.generateAuthX("/p/api/v1/album_grant/list_to_me", "GET", params)
            val response = service.getSharedAlbumsToMe(token, authx, 0, 1000).execute()
            if (!response.isSuccessful) {
                throw IOException("album_grant/list_to_me HTTP ${response.code()}")
            }
            response.body()?.data?.list.orEmpty().map { album ->
                val owner = album.ownerName?.takeIf { it.isNotBlank() }
                val count = mediaCountText(album.photoCount, album.videoCount)
                CollectionItem(
                    id = album.albumId.toString(),
                    title = album.albumName.orEmpty().ifBlank { "未命名共享相册" },
                    subtitle = listOfNotNull(owner, count.takeIf { it.isNotBlank() }).joinToString(" · "),
                    type = "shared_album",
                    imageUrl = absoluteNullable(album.posterUrl ?: album.posterImgUrl),
                    sourceId = album.albumId,
                )
            }
        }
    }

    private fun loadSmartCategoryGrid() {
        loadCollectionGrid("smart", "智能分类") {
            fetchSmartCategories("scene", "场景与物体") +
                fetchSmartCategories("information", "信息")
        }
    }

    private fun loadTagGrid() {
        loadCollectionGrid(
            action = "tags",
            title = "标签",
            emptyMessage = "暂无标签，请在飞牛相册网页或移动端为照片添加标签",
        ) {
            displayBrowseRepository
                ?.loadTags()
                .orEmpty()
                .map { category ->
                    val source = category.source as DisplayCategorySource.Tag
                    val streamSource = StreamSource.tag(source.name, category.title) as StreamSource.Tag
                    CollectionItem(
                        id = streamSource.collectionCardId(),
                        title = category.title,
                        subtitle = countText(category.count),
                        type = "tag",
                        imageUrl = absoluteNullable(category.posterPath),
                        sourceId = 0,
                        payload = source.name,
                        fallbackDrawableRes = R.drawable.ic_tag_display,
                    )
                }
        }
    }

    private fun loadMediaTypeGrid() {
        loadCollectionGrid("media_types", "媒体类型") {
            displayBrowseRepository
                ?.loadMediaTypes()
                .orEmpty()
                .map { category ->
                    val source = category.source as DisplayCategorySource.MediaType
                    CollectionItem(
                        id = category.id,
                        title = category.title,
                        subtitle = countText(category.count),
                        type = "media_type",
                        imageUrl = absoluteNullable(category.posterPath),
                        sourceId = source.categoryCode,
                        payload = source.fileType,
                        layout = CollectionLayout.Wide,
                        fallbackDrawableRes = R.drawable.ic_media_type_display,
                    )
                }
        }
    }

    private fun fetchSmartCategories(mainCategory: String, groupTitle: String): List<CollectionItem> {
        val service = api ?: return emptyList()
        val params = "limit=-1&mainCategory=$mainCategory"
        val authx = FnAuthUtils.generateAuthX("/p/api/v1/ai-smart-rec/categories", "GET", params)
        val response = service.getSmartCategories(token, authx, -1, mainCategory).execute()
        if (!response.isSuccessful) {
            throw IOException("ai-smart-rec/categories HTTP ${response.code()}")
        }
        val body = response.body()
        if (body?.code != 0) {
            throw IOException("ai-smart-rec/categories code ${body?.code}: ${body?.msg}")
        }
        return body.data?.list.orEmpty().map { category ->
            CollectionItem(
                id = "$mainCategory:${category.name}",
                title = category.name?.takeIf { it.isNotBlank() } ?: "未命名分类",
                subtitle = "$groupTitle · ${countText(category.itemCount)}",
                type = "smart_category",
                imageUrl = smartPosterUrl(category.poster),
                sourceId = 0,
                payload = category.name,
                secondaryPayload = mainCategory,
            )
        }
    }

    private fun smartPosterUrl(poster: FnHttpApi.SmartCategoryPoster?): String? {
        if (poster == null || poster.photoId <= 0 || poster.uuid.isNullOrBlank()) {
            return null
        }
        return absoluteUrl("/p/api/v1/stream/p/t/${poster.photoId}/s/${poster.uuid}")
    }

    private fun showSettingsPage() {
        legacyAction = null
        selectedAction = "settings"
        lastLegacyAction = null
        pageDepth = 0
        removeLegacyFragment()
        clearHero()

        homeLoadJob?.cancel()
        homePageJob?.cancel()
        collectionJob?.cancel()
        currentHomePhotos = emptyList()
        collectionState = null
    }

    private fun showPlaceholderGrid(action: String, title: String) {
        legacyAction = null
        selectedAction = action
        pageDepth = 0
        removeLegacyFragment()
        clearHero()

        homeLoadJob?.cancel()
        homePageJob?.cancel()
        collectionJob?.cancel()
        currentHomePhotos = emptyList()
        collectionState = CollectionUiState.Empty(title)
    }

    private fun loadFolderBrowserGrid(folderPath: String, title: String) {
        legacyAction = null
        selectedAction = "folders"
        lastLegacyAction = null
        pageDepth = 1
        removeLegacyFragment()
        clearHero()

        homeLoadJob?.cancel()
        homePageJob?.cancel()
        collectionJob?.cancel()
        currentHomePhotos = emptyList()
        currentStreamSource = StreamSource.folder(folderPath, title)
        resetGalleryPaging()
        collectionState = CollectionUiState.Loading(title)

        collectionJob = activityScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    fetchFolderBrowserItems(folderPath)
                }
            }

            result
                .onSuccess { browserResult ->
                    currentHomePhotos = browserResult.photos
                    collectionState = if (browserResult.items.isEmpty()) {
                        CollectionUiState.Empty(title)
                    } else {
                        updateCollectionHero(browserResult.items.firstOrNull())
                        CollectionUiState.Content(title, browserResult.items)
                    }
                }
                .onFailure { throwable ->
                    Log.e(TAG, "Load folder browser failed", throwable)
                    currentHomePhotos = emptyList()
                    collectionState = CollectionUiState.Error(title, "${title}加载失败")
                }
        }
    }

    private fun fetchFolderBrowserItems(folderPath: String): FolderBrowserResult {
        val service = api ?: return FolderBrowserResult(emptyList(), emptyList())
        val folderParams = "desc=false&folderPath=$folderPath&orderBy=2"
        val folderAuthx = FnAuthUtils.generateAuthX("/p/api/v1/folder_view/getFolderList", "GET", folderParams)
        val folderResponse = service
            .getSubFolders(token, folderAuthx, folderPath, false, 2)
            .execute()
        if (!folderResponse.isSuccessful) {
            throw IOException("folder_view/getFolderList HTTP ${folderResponse.code()}")
        }

        val folderItems = folderResponse.body()?.data?.list.orEmpty().map { folder ->
            val path = folder.path.orEmpty()
            CollectionItem(
                id = path.ifBlank { folder.name.orEmpty() },
                title = folder.name?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/').ifBlank { "未命名文件夹" },
                subtitle = "文件夹",
                type = "folder",
                imageUrl = null,
                sourceId = 0,
                payload = path,
            )
        }

        val fileParams = "desc=false&folderPath=$folderPath&limit=$INITIAL_PHOTO_LIMIT&offset=0&orderBy=2"
        val fileAuthx = FnAuthUtils.generateAuthX("/p/api/v1/folder_view/getFileList", "GET", fileParams)
        val fileResponse = service
            .getFolderFiles(token, fileAuthx, folderPath, false, 2, INITIAL_PHOTO_LIMIT, 0)
            .execute()
        if (!fileResponse.isSuccessful) {
            throw IOException("folder_view/getFileList HTTP ${fileResponse.code()}")
        }

        val photos = fileResponse.body()?.data?.list.orEmpty()
            .map { PhotoStreamMapper.fromFolderMediaItem(it, baseUrl) }
        val photoItems = photos.map { photo ->
            CollectionItem(
                id = photo.id,
                title = photo.title,
                subtitle = "",
                type = "folder_photo",
                imageUrl = photo.thumbnailUrl,
                sourceId = photo.id.toIntOrNull() ?: 0,
                payload = photo.id,
                photo = photo,
            )
        }

        return FolderBrowserResult(folderItems + photoItems, photos)
    }

    private fun loadCollectionGrid(
        action: String,
        title: String,
        emptyMessage: String = "${title}暂无内容",
        fetchItems: suspend () -> List<CollectionItem>,
    ) {
        legacyAction = null
        selectedAction = action
        pageDepth = 0
        removeLegacyFragment()
        clearHero()

        homeLoadJob?.cancel()
        homePageJob?.cancel()
        collectionJob?.cancel()
        currentHomePhotos = emptyList()
        collectionState = CollectionUiState.Loading(title)
        val publicationOwner = loadPublicationGate.begin("collection:$action")

        collectionJob = activityScope.launch {
            val result = try {
                Result.success(withContext(Dispatchers.IO) {
                    fetchItems()
                })
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }

            result
                .onSuccess { items ->
                    loadPublicationGate.publish(publicationOwner) {
                        collectionState = if (items.isEmpty()) {
                            CollectionUiState.Empty(title, emptyMessage)
                        } else {
                            updateCollectionHero(items.firstOrNull())
                            CollectionUiState.Content(title, items)
                        }
                    }
                }
                .onFailure { throwable ->
                    loadPublicationGate.publish(publicationOwner) {
                        Log.e(TAG, "Load $action grid failed", throwable)
                        collectionState = CollectionUiState.Error(title, "${title}加载失败")
                    }
                }
        }
    }

    private fun handleCollectionSelection(item: CollectionItem) {
        when (item.type) {
            "album" -> {
                navigateTo(MainPageRoute.AlbumPhotos(item.sourceId, item.title, "albums"))
            }
            "shared_album" -> {
                navigateTo(MainPageRoute.AlbumPhotos(item.sourceId, item.title, "shared"))
            }
            "folder" -> {
                val folderPath = item.payload
                if (folderPath.isNullOrBlank()) {
                    Toast.makeText(this, "文件夹路径为空", Toast.LENGTH_SHORT).show()
                } else {
                    navigateTo(MainPageRoute.FolderBrowser(folderPath, item.title))
                }
            }
            "folder_photo" -> item.photo?.let { openMediaDetail(it) }
                ?: Toast.makeText(this, "无法打开照片", Toast.LENGTH_SHORT).show()
            "person" -> {
                navigateTo(MainPageRoute.DirectPhotos(StreamSource.person(item.sourceId, item.title)))
            }
            "place" -> {
                val country = item.payload.orEmpty()
                val city = item.secondaryPayload.orEmpty()
                if (country.isBlank() && city.isBlank()) {
                    Toast.makeText(this, "地点信息为空", Toast.LENGTH_SHORT).show()
                } else {
                    navigateTo(MainPageRoute.DirectPhotos(StreamSource.place(country, city, item.title)))
                }
            }
            "smart_category" -> {
                val keyword = item.payload?.takeIf { it.isNotBlank() } ?: item.title
                navigateTo(MainPageRoute.DirectPhotos(StreamSource.smartCategory(keyword, item.title)))
            }
            "tag" -> {
                val name = item.payload.orEmpty()
                if (name.isBlank()) {
                    Toast.makeText(this, "标签名称为空", Toast.LENGTH_SHORT).show()
                } else {
                    navigateTo(MainPageRoute.DirectPhotos(StreamSource.tag(name, item.title)))
                }
            }
            "media_type" -> {
                val fileType = item.payload.orEmpty()
                if (fileType.isBlank()) {
                    Toast.makeText(this, "媒体类型为空", Toast.LENGTH_SHORT).show()
                } else {
                    navigateTo(
                        MainPageRoute.DirectPhotos(
                            StreamSource.mediaType(item.sourceId, fileType, item.title),
                        ),
                    )
                }
            }
            else -> Toast.makeText(this, "${item.title} 的详情页下一步重做", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCollectionHero(item: CollectionItem?) {
        heroState = item?.imageUrl
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if (item.type == "folder_photo") {
                    HeroState(imageUrl = it)
                } else {
                    HeroState(imageUrl = it, title = item.title, subtitle = item.subtitle)
                }
            }
            ?: HeroState()
    }

    private fun handleCollectionFocused(item: CollectionItem?) {
        updateCollectionHero(item)
        if (item == null) {
            return
        }
        currentFocusSnapshot = MainFocusSnapshot.Collection(item.toMainCollectionFocusTarget())
        if (item.type == "folder_photo" && item.photo != null) {
            val itemIndex = currentHomePhotos.indexOfFirst { it.id == item.photo.id }
            currentBrowsePosition = UserProfileStore.BrowsePosition.photo(
                selectedAction,
                currentStreamSource.browseSourceType(),
                currentStreamSource.browseSourceId(),
                currentStreamSource.browseSourcePayload(),
                currentStreamSource.title,
                item.photo.id,
                itemIndex,
            )
            return
        }
        val items = (collectionState as? CollectionUiState.Content)?.items.orEmpty()
        val itemIndex = items.indexOfFirst { it.type == item.type && it.id == item.id }
        currentBrowsePosition = UserProfileStore.BrowsePosition.collection(
            selectedAction,
            item.type,
            item.id,
            itemIndex,
        )
    }

    private fun updateHomeHero(photo: PhotoStreamPhoto?) {
        heroState = photo?.thumbnailUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { HeroState(imageUrl = it, title = "", subtitle = "") }
            ?: HeroState()
    }

    private fun handleHomePhotoFocused(photo: PhotoStreamPhoto) {
        if (MainHomeHeroPolicy.shouldUpdateHeroOnPhotoFocus(heroState.imageUrl, photo.thumbnailUrl)) {
            updateHomeHero(photo)
        }
        val itemIndex = currentHomePhotos.indexOfFirst { it.id == photo.id }
        currentFocusSnapshot = MainFocusSnapshot.Photo(HomePhotoFocusTarget(photo.id, itemIndex))
        currentBrowsePosition = UserProfileStore.BrowsePosition.photo(
            selectedAction,
            currentStreamSource.browseSourceType(),
            currentStreamSource.browseSourceId(),
            currentStreamSource.browseSourcePayload(),
            currentStreamSource.title,
            photo.id,
            itemIndex,
        )
    }

    private fun persistBrowsePositionIfNeeded() {
        if (suppressBrowsePositionSave || !profileStore.isRememberBrowsePositionEnabled) {
            return
        }
        profileStore.saveActiveBrowsePosition(currentBrowsePosition ?: return)
    }

    private fun showLegacyAction(action: String) {
        homeLoadJob?.cancel()
        legacyAction = action
        pageDepth = 0
        clearHero()
        lastLegacyAction = null
    }

    fun attachLegacyFragment(containerId: Int) {
        if (legacyFragment?.isAdded == true) {
            return
        }

        val fragment = MainFragment().apply {
            arguments = Bundle().apply {
                putString("nas_url", baseUrl)
                putString("api_token", token)
                putString("secret", secret)
            }
        }
        legacyFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .commitNowAllowingStateLoss()
    }

    fun dispatchLegacyAction(action: String) {
        val fragment = legacyFragment ?: return
        if (lastLegacyAction == action) {
            return
        }
        lastLegacyAction = action
        when (action) {
            "recent" -> fragment.loadRecent()
            "favorites" -> fragment.loadFavorites()
            "albums" -> fragment.loadAlbums()
            "folders" -> fragment.loadFolders()
            "people" -> fragment.loadPeople()
            "places" -> fragment.loadPlaces()
        }
    }

    fun showLoading(message: String?) {
        loadingMessage = message ?: "正在加载..."
    }

    fun hideLoading() {
        loadingMessage = null
    }

    private fun removeLegacyFragment() {
        val fragment = legacyFragment
        if (fragment != null && fragment.isAdded) {
            supportFragmentManager.beginTransaction()
                .remove(fragment)
                .commitNowAllowingStateLoss()
        }
        legacyFragment = null
    }

    private fun openMediaDetail(photo: PhotoStreamPhoto) {
        val mediaItems = currentHomePhotos.map { it.toMediaItem() }
        val currentIndex = mediaItems.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
        MediaListHolder.set(mediaItems)
        startActivity(
            Intent(this, MediaDetailActivity::class.java)
                .putExtra("CURRENT_INDEX", currentIndex),
        )
    }

    private fun PhotoStreamPhoto.toMediaItem(): MediaItem {
        return MediaItem(id, title, type, thumbnailUrl, mediaUrl).apply {
            dateStr = takenAt
        }
    }

    private fun logout() {
        suppressBrowsePositionSave = true
        profileStore.clearAll()

        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    private fun handleUpdateAction() {
        when (val state = appUpdateState) {
            AppUpdateUiState.Checking,
            is AppUpdateUiState.Downloading -> Unit
            AppUpdateUiState.Idle,
            is AppUpdateUiState.Error,
            is AppUpdateUiState.UpToDate -> checkForUpdates(force = true)
            is AppUpdateUiState.Available -> downloadAndInstallUpdate(state.release)
            is AppUpdateUiState.PermissionRequired -> {
                if (appUpdateInstaller.canRequestPackageInstalls()) {
                    downloadAndInstallUpdate(state.release)
                } else {
                    Toast.makeText(this, "请允许飞牛相册安装未知来源应用", Toast.LENGTH_LONG).show()
                    appUpdateInstaller.openInstallPermissionSettings()
                }
            }
            is AppUpdateUiState.ReadyToInstall -> installDownloadedUpdate(File(state.apkPath))
        }
    }

    private fun checkForUpdates(force: Boolean) {
        updateCheckJob?.cancel()
        updateCheckJob = activityScope.launch {
            if (force) {
                appUpdateState = AppUpdateUiState.Checking
            }

            when (val result = appUpdateChecker.check(force)) {
                is AppUpdateCheckResult.Skipped -> Unit
                is AppUpdateCheckResult.UpdateAvailable -> {
                    appUpdateState = AppUpdateUiState.Available(result.release)
                    Toast.makeText(
                        this@MainActivity,
                        "发现新版本 ${result.release.version}，可在设置中更新",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                is AppUpdateCheckResult.UpToDate -> {
                    if (force) {
                        appUpdateState = AppUpdateUiState.UpToDate(result.release.version)
                        Toast.makeText(this@MainActivity, "已是最新版本", Toast.LENGTH_SHORT).show()
                    }
                }
                is AppUpdateCheckResult.NoInstallableAsset -> {
                    if (force) {
                        appUpdateState = AppUpdateUiState.Error("最新版本没有可安装 APK")
                    }
                }
                is AppUpdateCheckResult.Error -> {
                    Log.w(TAG, "Check app update failed", result.throwable)
                    if (force) {
                        appUpdateState = AppUpdateUiState.Error(result.message)
                        Toast.makeText(this@MainActivity, "检查更新失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun downloadAndInstallUpdate(release: AppReleaseInfo) {
        val asset = release.apkAsset
        if (asset == null) {
            appUpdateState = AppUpdateUiState.Error("最新版本没有可安装 APK")
            return
        }

        if (!appUpdateInstaller.canRequestPackageInstalls()) {
            appUpdateState = AppUpdateUiState.PermissionRequired(release)
            Toast.makeText(this, "需要先允许安装未知来源应用", Toast.LENGTH_LONG).show()
            return
        }

        updateDownloadJob?.cancel()
        updateDownloadJob = activityScope.launch {
            appUpdateState = AppUpdateUiState.Downloading(release)
            runCatching {
                appUpdateInstaller.downloadApk(asset)
            }.onSuccess { apkFile ->
                appUpdateState = AppUpdateUiState.ReadyToInstall(release, apkFile.absolutePath)
                installDownloadedUpdate(apkFile)
            }.onFailure { throwable ->
                Log.w(TAG, "Download app update failed", throwable)
                appUpdateState = AppUpdateUiState.Error(throwable.message ?: "下载失败，请稍后重试")
                Toast.makeText(this@MainActivity, "下载失败，请稍后重试", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun installDownloadedUpdate(apkFile: File) {
        runCatching {
            appUpdateInstaller.launchInstall(apkFile)
        }.onFailure { throwable ->
            Log.w(TAG, "Launch app update installer failed", throwable)
            appUpdateState = AppUpdateUiState.Error("无法打开系统安装器")
            Toast.makeText(this, "无法打开系统安装器", Toast.LENGTH_LONG).show()
        }
    }

    private fun logAppVersionAndStats() {
        val service = api ?: return
        activityScope.launch(Dispatchers.IO) {
            runCatching {
                val versionAuth = FnAuthUtils.generateAuthX("/p/api/v1/app/version", "GET", null)
                val version = service.getAppVersion(token, versionAuth).execute().body()?.data?.version
                if (!version.isNullOrBlank()) {
                    Log.i(TAG, "相册应用版本信息: $version")
                }

                val statsAuth = FnAuthUtils.generateAuthX("/p/api/v1/user_photo/stat", "GET", null)
                val stats = service.getPhotoStats(token, statsAuth).execute().body()?.data
                if (stats != null) {
                    Log.i(TAG, "相册统计信息: 照片 ${stats.photoCount}, 视频 ${stats.videoCount}, 管理员 ${stats.isAdmin}")
                }
            }.onFailure {
                Log.w(TAG, "Fetch app version or stats failed", it)
            }
        }
    }

    override fun updateHero(imageUrl: String?, title: String?, subtitle: String?) {
        val absoluteImage = imageUrl?.takeIf { it.isNotBlank() }?.let(::absoluteUrl)
        if (absoluteImage == null) {
            clearHero()
            return
        }
        heroState = HeroState(
            imageUrl = absoluteImage,
            title = title.orEmpty(),
            subtitle = subtitle.orEmpty(),
        )
    }

    override fun clearHero() {
        heroState = HeroState()
    }

    private fun absoluteUrl(path: String): String {
        return if (path.startsWith("http://") || path.startsWith("https://")) {
            path
        } else {
            baseUrl.trimEnd('/') + "/" + path.trimStart('/')
        }
    }

    private fun absoluteNullable(path: String?): String? {
        return path?.takeIf { it.isNotBlank() }?.let(::absoluteUrl)
    }

    private fun mediaCountText(photoCount: Int, videoCount: Int): String {
        val parts = mutableListOf<String>()
        if (photoCount > 0) {
            parts.add("${photoCount}张照片")
        }
        if (videoCount > 0) {
            parts.add("${videoCount}个视频")
        }
        return parts.joinToString(" · ").ifBlank { "暂无内容" }
    }

    private fun countText(count: Int): String {
        return if (count > 0) "${count}项" else "暂无内容"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (profileSwitcherVisible) {
                profileSwitcherVisible = false
                return true
            }
            if (legacyFragment?.onBackPressed() == true) {
                isBackPressedOnce = false
                return true
            }
            if (goBackInPageStack()) {
                isBackPressedOnce = false
                return true
            }
            if (currentRoute != MainPageRoute.Gallery) {
                resetToRoute(MainPageRoute.Gallery, MainFocusSnapshot.TopBar(MainTopFocusTarget.NavAction("gallery")))
                isBackPressedOnce = false
                return true
            }
            if (isBackPressedOnce) {
                finish()
                return true
            }
            isBackPressedOnce = true
            Toast.makeText(this, "再按一次退出应用", Toast.LENGTH_SHORT).show()
            backHandler.postDelayed({ isBackPressedOnce = false }, BACK_PRESS_INTERVAL)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun requestSelectedTopBarFocus(target: MainTopFocusTarget? = null) {
        pendingTopFocusTarget = target ?: MainTopFocusTarget.NavAction(selectedAction)
        topFocusRequestSerial += 1
    }

    private fun requestSettingsContentFocus() {
        settingsFocusRequestSerial += 1
    }

    private fun clearCollectionFocusTargets() {
        collectionRestoreFocusTarget = null
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionChangeListener?.let {
            getSharedPreferences(SessionPreferences.PREFS_NAME, Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        sessionChangeListener = null
        homeLoadJob?.cancel()
        homePageJob?.cancel()
        collectionJob?.cancel()
        updateCheckJob?.cancel()
        updateDownloadJob?.cancel()
        activityScope.cancel()
    }

    override fun onStop() {
        persistBrowsePositionIfNeeded()
        super.onStop()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val BACK_PRESS_INTERVAL = 2000L
        private const val INITIAL_PHOTO_LIMIT = 96
        private const val NEXT_PAGE_PHOTO_LIMIT = 72
        private const val RESTORE_PHOTO_SCAN_LIMIT = 1500
        private const val PER_DATE_FETCH_LIMIT = 36
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
    }
}

private sealed interface PhotoStreamUiState {
    data object Loading : PhotoStreamUiState
    data object Empty : PhotoStreamUiState
    data class Error(val message: String) : PhotoStreamUiState
    data class Content(
        val title: String,
        val groups: List<PhotoStreamGroup>,
        val loadingMore: Boolean,
        val hasMore: Boolean,
        val loadMoreError: String? = null,
    ) : PhotoStreamUiState
}

private fun StreamSource.browseSourceType(): String {
    return when (this) {
        StreamSource.Gallery -> "gallery"
        StreamSource.Recent -> "recent"
        StreamSource.Favorites -> "favorites"
        is StreamSource.Album -> "album"
        is StreamSource.Folder -> "folder"
        is StreamSource.Person -> "person"
        is StreamSource.Place -> "place"
        is StreamSource.SmartCategory -> "smart"
        is StreamSource.Tag -> "tag"
        is StreamSource.MediaType -> "media_type"
    }
}

private fun StreamSource.browseSourceId(): String {
    return when (this) {
        StreamSource.Gallery,
        StreamSource.Recent,
        StreamSource.Favorites,
        is StreamSource.Folder,
        is StreamSource.SmartCategory,
        -> ""
        is StreamSource.Album -> albumId.toString()
        is StreamSource.Person -> personId.toString()
        is StreamSource.Place -> country
        is StreamSource.Tag -> name
        is StreamSource.MediaType -> categoryCode.toString()
    }
}

private fun StreamSource.browseSourcePayload(): String {
    return when (this) {
        StreamSource.Gallery,
        StreamSource.Recent,
        StreamSource.Favorites,
        is StreamSource.Album,
        is StreamSource.Person,
        is StreamSource.Tag,
        -> ""
        is StreamSource.Folder -> folderPath
        is StreamSource.Place -> city
        is StreamSource.SmartCategory -> keyword
        is StreamSource.MediaType -> fileType
    }
}

private fun StreamSource.displayCategorySource(): DisplayCategorySource? {
    return when (this) {
        is StreamSource.Tag -> DisplayCategorySource.Tag(name)
        is StreamSource.MediaType -> DisplayCategorySource.MediaType(categoryCode, fileType)
        else -> null
    }
}

private fun StreamSource.displayLoadIdentity(): String {
    return when (this) {
        is StreamSource.Tag -> DisplayTagIdentity.fromRaw(name)
        is StreamSource.MediaType -> "media:$categoryCode:$fileType"
        else -> "$action:$title"
    }
}

private data class DirectPhotoLoadResult(
    val photos: List<PhotoStreamPhoto>,
    val offset: Int,
    val hasMore: Boolean,
)

private data class TimelineBucket(
    val year: Int,
    val month: Int,
    val day: Int,
    val itemCount: Int,
)

private sealed interface CollectionUiState {
    val title: String

    data class Loading(override val title: String) : CollectionUiState
    data class Empty(
        override val title: String,
        val message: String = "${title}暂无内容",
    ) : CollectionUiState
    data class Error(override val title: String, val message: String) : CollectionUiState
    data class Content(
        override val title: String,
        val items: List<CollectionItem>,
    ) : CollectionUiState
}

private data class CollectionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String,
    val imageUrl: String?,
    val sourceId: Int,
    val payload: String? = null,
    val secondaryPayload: String? = null,
    val photo: PhotoStreamPhoto? = null,
    val layout: CollectionLayout = CollectionLayout.Standard,
    val fallbackDrawableRes: Int? = null,
)

private enum class CollectionLayout {
    Standard,
    Wide,
}

private fun CollectionItem.toMainCollectionFocusItem(): MainCollectionFocusItem {
    return MainCollectionFocusItem(type = type, id = id)
}

private fun CollectionItem.toMainCollectionFocusTarget(): MainCollectionFocusTarget {
    return MainCollectionFocusTarget(type = type, id = id)
}

private data class FolderBrowserResult(
    val items: List<CollectionItem>,
    val photos: List<PhotoStreamPhoto>,
)

private data class HeroState(
    val imageUrl: String? = null,
    val title: String = "",
    val subtitle: String = "",
)

private data class HomePhotoFocusTarget(
    val photoId: String,
    val itemIndex: Int,
)

private sealed interface MainTopFocusTarget {
    data class NavAction(val action: String) : MainTopFocusTarget
    data object Settings : MainTopFocusTarget
}

private sealed interface MainFocusSnapshot {
    data class TopBar(val target: MainTopFocusTarget) : MainFocusSnapshot
    data class Photo(val target: HomePhotoFocusTarget) : MainFocusSnapshot
    data class Collection(val target: MainCollectionFocusTarget) : MainFocusSnapshot
    data object SettingsContent : MainFocusSnapshot
}

private data class PhotoGridEntry(
    val key: String,
    val photoId: String,
    val gridIndex: Int,
)

private sealed interface AppUpdateUiState {
    val statusText: String
    val actionText: String

    data object Idle : AppUpdateUiState {
        override val statusText = "可从 GitHub Release 检查新版本"
        override val actionText = "检查"
    }

    data object Checking : AppUpdateUiState {
        override val statusText = "正在检查 GitHub Release"
        override val actionText = "检查中"
    }

    data class UpToDate(val version: String) : AppUpdateUiState {
        override val statusText = "已是最新版本 · $version"
        override val actionText = "重查"
    }

    data class Available(val release: AppReleaseInfo) : AppUpdateUiState {
        override val statusText = "发现 ${release.version}${release.apkAsset?.sizeBytes?.let { " · ${formatUpdateSize(it)}" }.orEmpty()}"
        override val actionText = "下载"
    }

    data class PermissionRequired(val release: AppReleaseInfo) : AppUpdateUiState {
        override val statusText = "需要允许本应用安装未知来源 APK"
        override val actionText = "授权"
    }

    data class Downloading(val release: AppReleaseInfo) : AppUpdateUiState {
        override val statusText = "正在下载 ${release.version}"
        override val actionText = "下载中"
    }

    data class ReadyToInstall(val release: AppReleaseInfo, val apkPath: String) : AppUpdateUiState {
        override val statusText = "安装包已下载，按确认打开系统安装器"
        override val actionText = "安装"
    }

    data class Error(val message: String) : AppUpdateUiState {
        override val statusText = message.take(42)
        override val actionText = "重试"
    }
}

private fun formatUpdateSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) {
        return "APK"
    }
    val mib = sizeBytes / 1024f / 1024f
    return String.format(Locale.US, "%.1f MB", mib)
}

@Composable
private fun TvPhotoApp(
    selectedAction: String,
    legacyAction: String?,
    navItems: List<TvNavItem>,
    homeState: PhotoStreamUiState,
    collectionState: CollectionUiState?,
    collectionRestoreFocusTarget: MainCollectionFocusTarget?,
    heroState: HeroState,
    loadingMessage: String?,
    token: String,
    serverUrl: String,
    currentProfileLabel: String,
    profiles: List<UserProfileStore.ProfileSummary>,
    profileSwitcherVisible: Boolean,
    currentVersionText: String,
    appUpdateState: AppUpdateUiState,
    topFocusRequestSerial: Int,
    settingsFocusRequestSerial: Int,
    topFocusTarget: MainTopFocusTarget,
    whiteThemeEnabled: Boolean,
    rememberBrowsePositionEnabled: Boolean,
    onNavSelected: (TvNavItem) -> Unit,
    onSettingsSelected: () -> Unit,
    onThemeToggle: () -> Unit,
    onUpdateAction: () -> Unit,
    onLogout: () -> Unit,
    onSwitchUser: () -> Unit,
    onProfileSelected: (UserProfileStore.ProfileSummary) -> Unit,
    onAddProfile: () -> Unit,
    onDismissProfileSwitcher: () -> Unit,
    onRememberBrowsePositionChanged: (Boolean) -> Unit,
    onPhotoSelected: (PhotoStreamPhoto) -> Unit,
    onPhotoFocused: (PhotoStreamPhoto) -> Unit,
    onLoadMore: () -> Unit,
    onCollectionSelected: (CollectionItem) -> Unit,
    onCollectionFocused: (CollectionItem?) -> Unit,
    onCollectionRestoreFocusConsumed: () -> Unit,
    homeRestoreFocusTarget: HomePhotoFocusTarget?,
    onHomeRestoreFocusConsumed: () -> Unit,
    onTopBarFocusChanged: (MainTopFocusTarget, Boolean) -> Unit,
    onContentFocused: () -> Unit,
    activity: MainActivity,
) {
    val themeColors = if (whiteThemeEnabled) WhiteTvThemeColors else DarkTvThemeColors
    CompositionLocalProvider(LocalFnTvThemeColors provides themeColors) {
        androidx.tv.material3.MaterialTheme {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(themeColors.background),
            ) {
                HeroBackdrop(heroState = heroState, token = token)

                if (legacyAction == null) {
                    val visibleCollectionState = collectionState
                    if (selectedAction == "settings") {
                        SettingsSurface(
                            serverUrl = serverUrl,
                            currentProfileLabel = currentProfileLabel,
                            currentVersionText = currentVersionText,
                            appUpdateState = appUpdateState,
                            rememberBrowsePositionEnabled = rememberBrowsePositionEnabled,
                            focusRequestSerial = settingsFocusRequestSerial,
                            onUpdateAction = onUpdateAction,
                            onSwitchUser = onSwitchUser,
                            onRememberBrowsePositionChanged = onRememberBrowsePositionChanged,
                            onLogout = onLogout,
                            onContentFocused = onContentFocused,
                        )
                    } else if (visibleCollectionState != null) {
                        CollectionSurface(
                            state = visibleCollectionState,
                            token = token,
                            restoreFocusTarget = collectionRestoreFocusTarget,
                            onItemSelected = onCollectionSelected,
                            onItemFocused = onCollectionFocused,
                            onRestoreFocusTargetConsumed = onCollectionRestoreFocusConsumed,
                            onContentFocused = onContentFocused,
                        )
                    } else {
                        HomePhotoStream(
                            state = homeState,
                            token = token,
                            onPhotoSelected = onPhotoSelected,
                            onPhotoFocused = onPhotoFocused,
                            onLoadMore = onLoadMore,
                            restoreFocusTarget = homeRestoreFocusTarget,
                            onRestoreFocusTargetConsumed = onHomeRestoreFocusConsumed,
                            onContentFocused = onContentFocused,
                        )
                    }
                } else {
                    LegacyFragmentHost(
                        activity = activity,
                        action = legacyAction,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                TvTopNavigationBar(
                    items = navItems,
                    selectedAction = selectedAction,
                    focusRequestSerial = topFocusRequestSerial,
                    focusTarget = topFocusTarget,
                    whiteThemeEnabled = whiteThemeEnabled,
                    onItemSelected = onNavSelected,
                    onSettingsSelected = onSettingsSelected,
                    onThemeToggle = onThemeToggle,
                    onTopBarFocusChanged = onTopBarFocusChanged,
                )

                if (loadingMessage != null) {
                    LoadingOverlay(message = loadingMessage)
                }

                if (profileSwitcherVisible) {
                    ProfileSwitcherDialog(
                        profiles = profiles,
                        onProfileSelected = onProfileSelected,
                        onAddProfile = onAddProfile,
                        onDismiss = onDismissProfileSwitcher,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroBackdrop(
    heroState: HeroState,
    token: String,
) {
    val themeColors = LocalFnTvThemeColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(themeColors.surfaceRaised, themeColors.background),
                    radius = 1450f,
                ),
            ),
    )

    val imageUrl = heroState.imageUrl
    if (!imageUrl.isNullOrBlank()) {
        AuthenticatedImage(
            url = imageUrl,
            token = token,
            targetWidth = 1280,
            targetHeight = 720,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.34f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            themeColors.background.copy(alpha = 0.96f),
                            themeColors.background.copy(alpha = 0.62f),
                            themeColors.background.copy(alpha = 0.24f),
                            themeColors.background.copy(alpha = 0.72f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun LegacyFragmentHost(
    activity: MainActivity,
    action: String,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                id = R.id.main_content_container
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                activity.attachLegacyFragment(id)
            }
        },
        update = {
            activity.dispatchLegacyAction(action)
        },
    )
}

@Composable
private fun TvTopNavigationBar(
    items: List<TvNavItem>,
    selectedAction: String,
    focusRequestSerial: Int,
    focusTarget: MainTopFocusTarget,
    whiteThemeEnabled: Boolean,
    onItemSelected: (TvNavItem) -> Unit,
    onSettingsSelected: () -> Unit,
    onThemeToggle: () -> Unit,
    onTopBarFocusChanged: (MainTopFocusTarget, Boolean) -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    val navListState = rememberLazyListState()
    val navFocusRequesters = remember {
        items.associate { it.action to FocusRequester() }
    }
    val settingsFocusRequester = remember { FocusRequester() }
    var initialFocusApplied by remember { mutableStateOf(false) }

    suspend fun requestSelectedTopFocus(preScrollAllowed: Boolean) {
        val targetAction = when (focusTarget) {
            is MainTopFocusTarget.NavAction -> focusTarget.action
            MainTopFocusTarget.Settings -> ""
        }
        if (focusTarget == MainTopFocusTarget.Settings || (selectedAction == "settings" && targetAction.isBlank())) {
            withFrameNanos { }
            runCatching { settingsFocusRequester.requestFocus() }
            return
        }

        val actionToFocus = targetAction.ifBlank { selectedAction }
        val index = items.indexOfFirst { it.action == actionToFocus }
        if (index >= 0) {
            val visibleIndexes = navListState.layoutInfo.visibleItemsInfo.map { it.index }
            if (MainTopNavigationFocusPolicy.focusScrollBehavior(
                    selectedIndex = index,
                    visibleIndexes = visibleIndexes,
                    preScrollAllowed = preScrollAllowed,
                ) == MainTopNavigationFocusPolicy.FocusScrollBehavior.Animated
            ) {
                navListState.animateScrollToItem(index)
            }
            withFrameNanos { }
            runCatching { navFocusRequesters[actionToFocus]?.requestFocus() }
        } else if (selectedAction == "settings") {
            withFrameNanos { }
            runCatching { settingsFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(Unit) {
        if (!initialFocusApplied) {
            delay(250)
            requestSelectedTopFocus(preScrollAllowed = true)
            delay(250)
            requestSelectedTopFocus(preScrollAllowed = true)
            initialFocusApplied = true
        }
    }

    LaunchedEffect(focusRequestSerial) {
        if (focusRequestSerial <= 0) {
            return@LaunchedEffect
        }
        requestSelectedTopFocus(preScrollAllowed = false)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopNavHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(themeColors.topBarStart, themeColors.topBarMid, Color.Transparent),
                ),
            )
            .padding(start = 72.dp, end = 54.dp, top = 28.dp, bottom = 18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .height(TopBarMetrics.STATUS_CLUSTER_HEIGHT_DP.dp)
                .padding(end = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "飞牛相册",
                modifier = Modifier
                    .size(TopBarMetrics.APP_LOGO_SIZE_DP.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }

        LazyRow(
            modifier = Modifier
                .weight(1f)
                .padding(end = 30.dp),
            state = navListState,
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.Top,
        ) {
            lazyRowItems(items, key = { it.action }) { item ->
                TopNavItem(
                    item = item,
                    selected = item.action == selectedAction,
                    focusRequester = navFocusRequesters.getValue(item.action),
                    onClick = { onItemSelected(item) },
                    onFocusChanged = { focused -> onTopBarFocusChanged(MainTopFocusTarget.NavAction(item.action), focused) },
                )
            }
        }

        TopStatusCluster(
            selected = selectedAction == "settings",
            selectedAction = selectedAction,
            focusRequester = settingsFocusRequester,
            whiteThemeEnabled = whiteThemeEnabled,
            onSettingsSelected = onSettingsSelected,
            onThemeToggle = onThemeToggle,
            onFocusChanged = onTopBarFocusChanged,
        )
    }
}

@Composable
private fun TopStatusCluster(
    selected: Boolean,
    selectedAction: String,
    focusRequester: FocusRequester,
    whiteThemeEnabled: Boolean,
    onSettingsSelected: () -> Unit,
    onThemeToggle: () -> Unit,
    onFocusChanged: (MainTopFocusTarget, Boolean) -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    val themeFocusRequester = remember { FocusRequester() }
    var timeText by remember { mutableStateOf(currentTimeText()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            timeText = currentTimeText()
        }
    }

    Row(
        modifier = Modifier
            .height(TopBarMetrics.STATUS_CLUSTER_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopThemeButton(
            contentDescription = if (whiteThemeEnabled) "切换黑色主题" else "切换白色主题",
            selected = whiteThemeEnabled,
            focusRequester = themeFocusRequester,
            onClick = onThemeToggle,
            onFocusChanged = { focused -> onFocusChanged(MainTopFocusTarget.NavAction(selectedAction), focused) },
            animationLabel = "themeTopScale",
        )
        TopIconButton(
            icon = "\u2699",
            contentDescription = "设置",
            selected = selected,
            focusRequester = focusRequester,
            onClick = onSettingsSelected,
            onFocusChanged = { focused -> onFocusChanged(MainTopFocusTarget.Settings, focused) },
            animationLabel = "settingsTopScale",
            iconFontSize = TopBarMetrics.SETTINGS_ICON_SIZE_DP,
        )
        BasicText(
            text = timeText,
            style = TextStyle(
                color = themeColors.secondaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 27.sp,
            )
        )
    }
}

@Composable
private fun TopThemeButton(
    contentDescription: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    animationLabel: String,
) {
    TopButtonSurface(
        contentDescription = contentDescription,
        selected = selected,
        focusRequester = focusRequester,
        onClick = onClick,
        onFocusChanged = onFocusChanged,
        animationLabel = animationLabel,
    ) { focused ->
        ThemeToggleIcon(emphasized = focused || selected)
    }
}

@Composable
private fun TopIconButton(
    icon: String,
    contentDescription: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    animationLabel: String,
    iconFontSize: Int,
) {
    val themeColors = LocalFnTvThemeColors.current
    TopButtonSurface(
        contentDescription = contentDescription,
        selected = selected,
        focusRequester = focusRequester,
        onClick = onClick,
        onFocusChanged = onFocusChanged,
        animationLabel = animationLabel,
    ) { focused ->
        BasicText(
            text = icon,
            style = TextStyle(
                color = if (focused || selected) themeColors.primaryText else themeColors.secondaryText,
                fontWeight = FontWeight.Medium,
                fontSize = iconFontSize.sp,
            ),
        )
    }
}

@Composable
private fun TopButtonSurface(
    contentDescription: String,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    animationLabel: String,
    content: @Composable (Boolean) -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = animationLabel)
    val backgroundColor = when {
        focused -> themeColors.surfaceRaised.copy(alpha = 0.94f)
        selected -> themeColors.surfaceRaised.copy(alpha = 0.62f)
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .size(TopBarMetrics.STATUS_BUTTON_SIZE_DP.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .semantics { this.contentDescription = contentDescription }
            .tvFocusableClick(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val visualShape = RoundedCornerShape((TopBarMetrics.STATUS_BUTTON_VISUAL_SIZE_DP / 2).dp)
        Box(
            modifier = Modifier
                .size(TopBarMetrics.STATUS_BUTTON_VISUAL_SIZE_DP.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .clip(visualShape)
                .background(backgroundColor)
                .border(
                    width = if (focused) 2.dp else 0.dp,
                    color = if (focused) themeColors.focus else Color.Transparent,
                    shape = visualShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            content(focused)
        }
    }
}

@Composable
private fun ThemeToggleIcon(emphasized: Boolean) {
    val themeColors = LocalFnTvThemeColors.current
    val outline = if (emphasized) themeColors.primaryText else themeColors.secondaryText
    val darkHalf = if (emphasized) themeColors.primaryText else themeColors.secondaryText
    val lightHalf = themeColors.surfaceRaised
    Canvas(modifier = Modifier.size(TopBarMetrics.THEME_ICON_SIZE_DP.dp)) {
        val diameter = size.minDimension
        val strokeWidth = 2.dp.toPx()
        val iconSize = Size(diameter, diameter)
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f,
        )
        val center = Offset(
            x = topLeft.x + diameter / 2f,
            y = topLeft.y + diameter / 2f,
        )

        drawCircle(
            color = darkHalf,
            radius = diameter / 2f,
            center = center,
        )
        drawArc(
            color = lightHalf,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = topLeft,
            size = iconSize,
        )
        drawCircle(
            color = outline,
            radius = diameter / 2f - strokeWidth / 2f,
            center = center,
            style = Stroke(width = strokeWidth),
        )
    }
}

@Composable
private fun TopNavItem(
    item: TvNavItem,
    selected: Boolean,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "topNavScale")
    val showUnderline = MainTopNavigationFocusPolicy.shouldShowUnderline(selected, focused)
    val underlineWidth by animateDpAsState(
        targetValue = if (showUnderline) 46.dp else 0.dp,
        label = "topNavUnderline",
    )
    val textColor = when (MainTopNavigationFocusPolicy.topNavTextTone(selected, focused, item.enabled)) {
        MainTopNavigationFocusPolicy.TopNavTextTone.Primary -> themeColors.primaryText
        MainTopNavigationFocusPolicy.TopNavTextTone.Focus -> themeColors.focus
        MainTopNavigationFocusPolicy.TopNavTextTone.Disabled -> themeColors.secondaryText.copy(alpha = 0.52f)
        MainTopNavigationFocusPolicy.TopNavTextTone.Secondary -> themeColors.secondaryText
    }

    Column(
        modifier = Modifier
            .height(TopBarMetrics.NAV_ITEM_HEIGHT_DP.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .tvFocusableClick(onClick = onClick)
            .padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.height(TopBarMetrics.STATUS_CLUSTER_HEIGHT_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = item.title,
                style = TextStyle(
                    color = textColor,
                    fontWeight = if (focused || selected) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 22.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(underlineWidth)
                .height(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (focused) themeColors.focus else themeColors.secondaryText),
        )
    }
}

@Composable
private fun HomePhotoStream(
    state: PhotoStreamUiState,
    token: String,
    onPhotoSelected: (PhotoStreamPhoto) -> Unit,
    onPhotoFocused: (PhotoStreamPhoto) -> Unit,
    onLoadMore: () -> Unit,
    restoreFocusTarget: HomePhotoFocusTarget?,
    onRestoreFocusTargetConsumed: () -> Unit,
    onContentFocused: () -> Unit,
) {
    when (state) {
        PhotoStreamUiState.Loading -> CenterMessage("正在加载图库...")
        PhotoStreamUiState.Empty -> CenterMessage("还没有照片")
        is PhotoStreamUiState.Error -> CenterMessage(state.message, isError = true)
        is PhotoStreamUiState.Content -> PhotoGroupList(
            groups = state.groups,
            loadingMore = state.loadingMore,
            hasMore = state.hasMore,
            loadMoreError = state.loadMoreError,
            token = token,
            onPhotoSelected = onPhotoSelected,
            onPhotoFocused = onPhotoFocused,
            onLoadMore = onLoadMore,
            restoreFocusTarget = restoreFocusTarget,
            onRestoreFocusTargetConsumed = onRestoreFocusTargetConsumed,
            onContentFocused = onContentFocused,
        )
    }
}

@Composable
private fun CollectionSurface(
    state: CollectionUiState,
    token: String,
    restoreFocusTarget: MainCollectionFocusTarget?,
    onItemSelected: (CollectionItem) -> Unit,
    onItemFocused: (CollectionItem?) -> Unit,
    onRestoreFocusTargetConsumed: () -> Unit,
    onContentFocused: () -> Unit,
) {
    when (state) {
        is CollectionUiState.Loading -> CenterMessage("正在加载${state.title}...")
        is CollectionUiState.Empty -> CenterMessage(state.message)
        is CollectionUiState.Error -> CenterMessage(state.message, isError = true)
        is CollectionUiState.Content -> CollectionGrid(
            title = state.title,
            items = state.items,
            token = token,
            restoreFocusTarget = restoreFocusTarget,
            onItemSelected = onItemSelected,
            onItemFocused = onItemFocused,
            onRestoreFocusTargetConsumed = onRestoreFocusTargetConsumed,
            onContentFocused = onContentFocused,
        )
    }
}

@Composable
private fun CollectionGrid(
    title: String,
    items: List<CollectionItem>,
    token: String,
    restoreFocusTarget: MainCollectionFocusTarget?,
    onItemSelected: (CollectionItem) -> Unit,
    onItemFocused: (CollectionItem?) -> Unit,
    onRestoreFocusTargetConsumed: () -> Unit,
    onContentFocused: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val layout = items.firstOrNull()?.layout ?: CollectionLayout.Standard
    val focusItems = remember(items) {
        items.map { it.toMainCollectionFocusItem() }
    }
    val focusRequesters = remember(focusItems) {
        focusItems.associateWith { FocusRequester() }
    }
    val restoreFocusItem = remember(restoreFocusTarget, focusItems) {
        val itemIndex = MainCollectionFocusPolicy.findItemIndex(focusItems, restoreFocusTarget)
        focusItems.getOrNull(itemIndex)
    }
    var restoreFocusConfirmed by remember(restoreFocusTarget) { mutableStateOf(false) }

    LaunchedEffect(restoreFocusTarget, restoreFocusItem) {
        restoreFocusTarget ?: return@LaunchedEffect
        val focusItem = restoreFocusItem
        if (focusItem == null) {
            if (
                MainFocusRestorePolicy.shouldConsumeTarget(
                    targetFound = false,
                    focusConfirmed = false,
                )
            ) {
                onRestoreFocusTargetConsumed()
            }
            return@LaunchedEffect
        }

        val itemIndex = focusItems.indexOf(focusItem)
        val gridIndex = MainCollectionFocusPolicy.lazyGridIndexForItemIndex(itemIndex)
        gridState.scrollToItem(gridIndex)
        while (!restoreFocusConfirmed) {
            withFrameNanos { }
            runCatching { focusRequesters[focusItem]?.requestFocus() }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(
            minSize = if (layout == CollectionLayout.Wide) 360.dp else 264.dp,
        ),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = ContentHorizontalPadding,
                top = ContentTopPadding,
                end = ContentHorizontalPadding,
                bottom = 44.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(
            key = "collection-title-$title",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            CollectionHeader(title = title)
        }

        lazyGridItems(
            items = items,
            key = { item -> "collection-${item.type}-${item.id}" },
        ) { item ->
            val focusItem = item.toMainCollectionFocusItem()
            CollectionCard(
                item = item,
                token = token,
                focusRequester = focusRequesters.getValue(focusItem),
                onClick = { onItemSelected(item) },
                onFocused = {
                    val restored = focusItem == restoreFocusItem
                    if (restored) {
                        restoreFocusConfirmed = true
                    }
                    onContentFocused()
                    onItemFocused(item)
                    if (
                        restored &&
                        MainFocusRestorePolicy.shouldConsumeTarget(
                            targetFound = true,
                            focusConfirmed = true,
                        )
                    ) {
                        onRestoreFocusTargetConsumed()
                    }
                },
            )
        }
    }
}

@Composable
private fun CollectionHeader(title: String) {
    val themeColors = LocalFnTvThemeColors.current
    BasicText(
        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
        text = title,
        style = TextStyle(
            color = themeColors.primaryText,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun CollectionCard(
    item: CollectionItem,
    token: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.035f else 1f, label = "collectionScale")
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(
                if (item.layout == CollectionLayout.Wide) 2.05f else 1.55f,
            )
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(shape)
            .background(themeColors.surface)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) themeColors.focus else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusRequester(focusRequester)
            .tvFocusableClick(onClick = onClick),
    ) {
        val imageUrl = item.imageUrl
        if (imageUrl.isNullOrBlank()) {
            CollectionPlaceholder(
                title = item.title,
                drawableRes = item.fallbackDrawableRes,
            )
        } else {
            AuthenticatedImage(
                url = imageUrl,
                token = token,
                targetWidth = 620,
                targetHeight = 400,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (item.type != "folder_photo") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                themeColors.background.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 22.dp, end = 22.dp, bottom = 18.dp),
            ) {
                BasicText(
                    text = item.title,
                    style = TextStyle(
                        color = themeColors.primaryText,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    BasicText(
                        text = item.subtitle,
                        style = TextStyle(
                            color = themeColors.primaryText.copy(alpha = 0.78f),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionPlaceholder(
    title: String,
    drawableRes: Int? = null,
) {
    val themeColors = LocalFnTvThemeColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(themeColors.surfaceRaised, themeColors.surface, themeColors.background),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (drawableRes != null) {
            Image(
                painter = painterResource(drawableRes),
                contentDescription = null,
                modifier = Modifier.size(78.dp),
            )
        } else {
            BasicText(
                text = title.trim().firstOrNull()?.toString().orEmpty().ifBlank { "•" },
                style = TextStyle(
                    color = themeColors.accent.copy(alpha = 0.78f),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}

@Composable
private fun PhotoGroupList(
    groups: List<PhotoStreamGroup>,
    loadingMore: Boolean,
    hasMore: Boolean,
    loadMoreError: String?,
    token: String,
    onPhotoSelected: (PhotoStreamPhoto) -> Unit,
    onPhotoFocused: (PhotoStreamPhoto) -> Unit,
    onLoadMore: () -> Unit,
    restoreFocusTarget: HomePhotoFocusTarget?,
    onRestoreFocusTargetConsumed: () -> Unit,
    onContentFocused: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val photoGridEntries = remember(groups) {
        val entries = mutableListOf<PhotoGridEntry>()
        var gridIndex = 0
        groups.forEachIndexed { groupIndex, group ->
            gridIndex += 1
            group.photos.forEachIndexed { photoIndex, photo ->
                entries.add(
                    PhotoGridEntry(
                        key = PhotoStreamRenderKeys.keyFor(groupIndex, photoIndex, photo.id),
                        photoId = photo.id,
                        gridIndex = gridIndex,
                    ),
                )
                gridIndex += 1
            }
        }
        entries
    }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    remember(photoGridEntries) {
        val activeKeys = photoGridEntries.mapTo(mutableSetOf()) { it.key }
        focusRequesters.keys.retainAll(activeKeys)
        activeKeys.forEach { key ->
            focusRequesters.getOrPut(key) { FocusRequester() }
        }
    }
    val restoreEntry = remember(restoreFocusTarget, photoGridEntries) {
        restoreFocusTarget?.let { target ->
            photoGridEntries.firstOrNull { it.photoId == target.photoId }
                ?: photoGridEntries.getOrNull(target.itemIndex)
        }
    }
    var restoreFocusConfirmed by remember(restoreFocusTarget) { mutableStateOf(false) }
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 8
        }
    }

    LaunchedEffect(restoreFocusTarget, restoreEntry) {
        restoreFocusTarget ?: return@LaunchedEffect
        val entry = restoreEntry
        if (entry == null) {
            if (
                MainFocusRestorePolicy.shouldConsumeTarget(
                    targetFound = false,
                    focusConfirmed = false,
                )
            ) {
                onRestoreFocusTargetConsumed()
            }
            return@LaunchedEffect
        }
        gridState.scrollToItem(entry.gridIndex)
        while (!restoreFocusConfirmed) {
            withFrameNanos { }
            runCatching { focusRequesters[entry.key]?.requestFocus() }
        }
    }

    LaunchedEffect(shouldLoadMore, hasMore, loadingMore) {
        if (shouldLoadMore && hasMore && !loadingMore) {
            onLoadMore()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 208.dp),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = ContentHorizontalPadding,
                top = ContentTopPadding,
                end = ContentHorizontalPadding,
                bottom = 44.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        groups.forEachIndexed { groupIndex, group ->
            item(
                key = "header-${group.title}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                DateHeader(title = group.title)
            }

            lazyGridItemsIndexed(
                items = group.photos,
                key = { photoIndex, photo -> PhotoStreamRenderKeys.keyFor(groupIndex, photoIndex, photo.id) },
            ) { photoIndex, photo ->
                val renderKey = PhotoStreamRenderKeys.keyFor(groupIndex, photoIndex, photo.id)
                PhotoTile(
                    photo = photo,
                    token = token,
                    focusRequester = focusRequesters.getValue(renderKey),
                    onClick = { onPhotoSelected(photo) },
                    onFocused = {
                        val restored = renderKey == restoreEntry?.key
                        if (restored) {
                            restoreFocusConfirmed = true
                        }
                        onContentFocused()
                        onPhotoFocused(photo)
                        if (
                            restored &&
                            MainFocusRestorePolicy.shouldConsumeTarget(
                                targetFound = true,
                                focusConfirmed = true,
                            )
                        ) {
                            onRestoreFocusTargetConsumed()
                        }
                    },
                )
            }
        }

        item(
            key = "gallery-load-more",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            LoadMoreRow(
                loadingMore = loadingMore,
                hasMore = hasMore,
                error = loadMoreError,
                onLoadMore = onLoadMore,
                onFocused = onContentFocused,
            )
        }
    }
}

@Composable
private fun DateHeader(title: String) {
    val themeColors = LocalFnTvThemeColors.current
    BasicText(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 4.dp),
        text = title,
        style = TextStyle(
            color = themeColors.primaryText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun LoadMoreRow(
    loadingMore: Boolean,
    hasMore: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    onFocused: () -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    val clickable = error != null || (hasMore && !loadingMore)
    val message = when {
        loadingMore -> "继续加载图库..."
        error != null -> "$error，按 OK 重试"
        hasMore -> "继续加载"
        else -> "已显示全部照片"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(themeColors.surface.copy(alpha = 0.44f))
            .onFocusChanged {
                if (it.isFocused) {
                    onFocused()
                }
            }
            .tvFocusableClick(clickable = clickable, focusable = clickable, onClick = onLoadMore),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = message,
            style = TextStyle(
                color = when {
                    error != null -> themeColors.negative
                    hasMore -> themeColors.accent
                    else -> themeColors.secondaryText
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun PhotoTile(
    photo: PhotoStreamPhoto,
    token: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f, label = "photoScale")
    val ratio = remember(photo.width, photo.height) {
        if (photo.width > 0 && photo.height > 0) {
            (photo.width.toFloat() / photo.height.toFloat()).coerceIn(0.78f, 1.55f)
        } else {
            1f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(4.dp))
            .background(themeColors.surface)
            .border(
                width = if (focused) 3.dp else 0.dp,
                color = if (focused) themeColors.focus else Color.Transparent,
                shape = RoundedCornerShape(4.dp),
            )
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusRequester(focusRequester)
            .tvFocusableClick(onClick = onClick),
    ) {
        AuthenticatedImage(
            url = photo.thumbnailUrl,
            token = token,
            targetWidth = PhotoTileImagePolicy.thumbnailDecodeSizePx,
            targetHeight = PhotoTileImagePolicy.thumbnailDecodeSizePx,
            modifier = Modifier.fillMaxSize(),
        )
        if (photo.type == "video") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xBB10100E))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                BasicText(
                    text = "视频",
                    style = TextStyle(
                        color = DarkTvThemeColors.primaryText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SettingsSurface(
    serverUrl: String,
    currentProfileLabel: String,
    currentVersionText: String,
    appUpdateState: AppUpdateUiState,
    rememberBrowsePositionEnabled: Boolean,
    focusRequestSerial: Int,
    onUpdateAction: () -> Unit,
    onSwitchUser: () -> Unit,
    onRememberBrowsePositionChanged: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onContentFocused: () -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    val switchUserFocusRequester = remember { FocusRequester() }
    val rememberPositionFocusRequester = remember { FocusRequester() }
    val updateFocusRequester = remember { FocusRequester() }
    val logoutFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(220)
        runCatching { switchUserFocusRequester.requestFocus() }
    }

    LaunchedEffect(focusRequestSerial) {
        if (focusRequestSerial > 0) {
            delay(80)
            runCatching { switchUserFocusRequester.requestFocus() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = ContentHorizontalPadding,
                top = ContentTopPadding + 18.dp,
                end = ContentHorizontalPadding,
                bottom = 44.dp,
            ),
    ) {
        BasicText(
            text = "设置",
            style = TextStyle(
                color = themeColors.primaryText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = Modifier.height(28.dp))
        SettingsInfoRow(
            title = "当前服务器",
            value = serverUrl.ifBlank { "未设置" },
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsInfoRow(
            title = "当前用户",
            value = currentProfileLabel,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsActionRow(
            title = "切换用户",
            subtitle = "选择已记录用户，或新增一个登录用户",
            actionLabel = "选择",
            focusRequester = switchUserFocusRequester,
            destructive = false,
            onClick = onSwitchUser,
            onFocused = onContentFocused,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsToggleRow(
            title = "记住浏览位置",
            checked = rememberBrowsePositionEnabled,
            focusRequester = rememberPositionFocusRequester,
            onCheckedChange = onRememberBrowsePositionChanged,
            onFocused = onContentFocused,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsActionRow(
            title = "软件更新",
            subtitle = appUpdateState.statusText,
            actionLabel = appUpdateState.actionText,
            focusRequester = updateFocusRequester,
            destructive = false,
            onClick = onUpdateAction,
            onFocused = onContentFocused,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsInfoRow(
            title = "当前版本",
            value = currentVersionText,
        )
        Spacer(modifier = Modifier.height(16.dp))
        SettingsActionRow(
            title = "退出登录",
            subtitle = "清除本机登录信息并返回登录页",
            actionLabel = "退出",
            focusRequester = logoutFocusRequester,
            destructive = true,
            onClick = onLogout,
            onFocused = onContentFocused,
        )
    }
}

@Composable
private fun ProfileSwitcherDialog(
    profiles: List<UserProfileStore.ProfileSummary>,
    onProfileSelected: (UserProfileStore.ProfileSummary) -> Unit,
    onAddProfile: () -> Unit,
    onDismiss: () -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    val addFocusRequester = remember { FocusRequester() }
    val profileFocusRequesters = remember(profiles) {
        profiles.associate { it.id to FocusRequester() }
    }

    LaunchedEffect(profiles) {
        delay(180)
        val firstProfile = profiles.firstOrNull()
        if (firstProfile != null) {
            runCatching { profileFocusRequesters[firstProfile.id]?.requestFocus() }
        } else {
            runCatching { addFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.loadingOverlay)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(720.dp)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(themeColors.surfaceRaised.copy(alpha = 0.98f))
                .border(2.dp, themeColors.focus.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
        ) {
            BasicText(
                text = "切换用户",
                style = TextStyle(
                    color = themeColors.primaryText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = Modifier.height(20.dp))

            profiles.forEach { profile ->
                SettingsActionRow(
                    title = profile.displayName,
                    subtitle = buildString {
                        append(profile.serverUrl)
                        if (profile.isActive) {
                            append(" · 当前")
                        } else if (!profile.hasSession()) {
                            append(" · 会话不可用")
                        } else if (!profile.hasCredentials()) {
                            append(" · token 过期后需重新登录")
                        }
                    },
                    actionLabel = if (profile.isActive) "当前" else "切换",
                    focusRequester = profileFocusRequesters.getValue(profile.id),
                    destructive = false,
                    onClick = { onProfileSelected(profile) },
                    onFocused = {},
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            SettingsActionRow(
                title = "新增用户",
                subtitle = "登录成功后保存为新的用户 profile",
                actionLabel = "登录",
                focusRequester = addFocusRequester,
                destructive = false,
                onClick = onAddProfile,
                onFocused = {},
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String,
) {
    val themeColors = LocalFnTvThemeColors.current
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .clip(shape)
            .background(themeColors.surface.copy(alpha = 0.46f))
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                color = themeColors.secondaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        BasicText(
            text = value,
            modifier = Modifier
                .weight(1f)
                .padding(start = 36.dp),
            style = TextStyle(
                color = themeColors.primaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    actionLabel: String,
    focusRequester: FocusRequester,
    destructive: Boolean = false,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.018f else 1f, label = "settingsActionRowScale")
    val shape = RoundedCornerShape(8.dp)
    val emphasisColor = if (destructive) themeColors.negative else themeColors.accent
    val borderColor = if (destructive) themeColors.negative else themeColors.focus

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(shape)
            .background(if (focused) themeColors.surfaceRaised.copy(alpha = 0.86f) else themeColors.surface.copy(alpha = 0.58f))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) borderColor else Color.Transparent,
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .tvFocusableClick(onClick = onClick)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = if (destructive) themeColors.negative else themeColors.primaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(modifier = Modifier.height(6.dp))
            BasicText(
                text = subtitle,
                style = TextStyle(
                    color = themeColors.secondaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        BasicText(
            text = actionLabel,
            style = TextStyle(
                color = if (focused) themeColors.primaryText else emphasisColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    focusRequester: FocusRequester,
    onCheckedChange: (Boolean) -> Unit,
    onFocused: () -> Unit,
) {
    val themeColors = LocalFnTvThemeColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.018f else 1f, label = "settingsRowScale")
    val shape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(shape)
            .background(if (focused) themeColors.surfaceRaised.copy(alpha = 0.86f) else themeColors.surface.copy(alpha = 0.58f))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) themeColors.focus else Color.Transparent,
                shape = shape,
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .tvFocusableClick(onClick = { onCheckedChange(!checked) })
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            text = title,
            style = TextStyle(
                color = themeColors.primaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        TogglePill(checked = checked, focused = focused)
    }
}

@Composable
private fun TogglePill(checked: Boolean, focused: Boolean) {
    val themeColors = LocalFnTvThemeColors.current
    Box(
        modifier = Modifier
            .width(64.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (checked) themeColors.focus else themeColors.secondaryText.copy(alpha = 0.46f))
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (focused) themeColors.primaryText else Color.White.copy(alpha = 0.9f)),
        )
    }
}

@Composable
private fun CenterMessage(message: String, isError: Boolean = false) {
    val themeColors = LocalFnTvThemeColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = TopNavHeight),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = message,
            style = TextStyle(
                color = if (isError) themeColors.negative else themeColors.accent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun LoadingOverlay(message: String) {
    val themeColors = LocalFnTvThemeColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.loadingOverlay),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = message,
            style = TextStyle(
                color = themeColors.primaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun AuthenticatedImage(
    url: String?,
    token: String,
    targetWidth: Int,
    targetHeight: Int,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            object : ImageView(context) {
                override fun onDetachedFromWindow() {
                    CachedImageLoader.clearImageView(this)
                    super.onDetachedFromWindow()
                }
            }.apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(android.graphics.Color.rgb(27, 26, 22))
            }
        },
        update = { imageView ->
            if (url.isNullOrBlank()) {
                CachedImageLoader.clearImageView(imageView)
                imageView.setImageDrawable(null)
                imageView.setTag(R.id.compose_image_url, null)
            } else if (imageView.getTag(R.id.compose_image_url) != url) {
                imageView.setTag(R.id.compose_image_url, url)
                CachedImageLoader.loadIntoImageView(imageView, url, token, targetWidth, targetHeight)
            }
        },
    )
}

private fun Modifier.tvFocusableClick(
    clickable: Boolean = true,
    focusable: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    return this
        .onPreviewKeyEvent { event ->
            if (!clickable || event.type != KeyEventType.KeyUp) {
                false
            } else if (
                event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter
            ) {
                onClick()
                true
            } else {
                false
            }
        }
        .clickable(enabled = clickable, onClick = onClick)
        .focusable(enabled = focusable)
}

private fun currentTimeText(): String {
    return SimpleDateFormat("H:mm", Locale.getDefault()).format(Date())
}

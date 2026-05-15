import React, { useRef, useState } from 'react';
import {
  View,
  StyleSheet,
  ActivityIndicator,
  TouchableOpacity,
  Text,
  Platform,
  BackHandler,
} from 'react-native';
import { WebView } from 'react-native-webview';
import { useFocusEffect } from '@react-navigation/native';

// Real mobile User-Agents so social sites serve their mobile UI
// instead of redirecting to "open in app" walls.
const USER_AGENT =
  Platform.OS === 'ios'
    ? 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1'
    : 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36';

export default function WebViewScreen({ route, navigation }) {
  const { url, title } = route.params;
  const webRef = useRef(null);
  const [loading, setLoading] = useState(true);
  const [canGoBack, setCanGoBack] = useState(false);

  // Hardware back on Android navigates within the WebView first
  useFocusEffect(
    React.useCallback(() => {
      const onBack = () => {
        if (canGoBack && webRef.current) {
          webRef.current.goBack();
          return true;
        }
        return false;
      };
      const sub = BackHandler.addEventListener('hardwareBackPress', onBack);
      return () => sub.remove();
    }, [canGoBack])
  );

  return (
    <View style={styles.container}>
      <View style={styles.toolbar}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.btn}
        >
          <Text style={styles.btnText}>‹ Apps</Text>
        </TouchableOpacity>
        <Text style={styles.title} numberOfLines={1}>
          {title}
        </Text>
        <TouchableOpacity
          onPress={() => webRef.current?.reload()}
          style={styles.btn}
        >
          <Text style={styles.btnText}>↻</Text>
        </TouchableOpacity>
      </View>

      <WebView
        ref={webRef}
        source={{ uri: url }}
        userAgent={USER_AGENT}
        originWhitelist={['*']}
        javaScriptEnabled
        domStorageEnabled
        sharedCookiesEnabled
        thirdPartyCookiesEnabled
        allowsInlineMediaPlayback
        mediaPlaybackRequiresUserAction={false}
        setSupportMultipleWindows={false}
        pullToRefreshEnabled
        startInLoadingState
        onLoadStart={() => setLoading(true)}
        onLoadEnd={() => setLoading(false)}
        onNavigationStateChange={(nav) => setCanGoBack(nav.canGoBack)}
        style={{ flex: 1, backgroundColor: '#fff' }}
      />

      {loading && (
        <View style={styles.loader} pointerEvents="none">
          <ActivityIndicator size="large" />
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#0b1020' },
  toolbar: {
    height: 52,
    paddingHorizontal: 12,
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#0b1020',
  },
  btn: { paddingHorizontal: 10, paddingVertical: 6 },
  btnText: { color: '#fff', fontSize: 16, fontWeight: '700' },
  title: {
    flex: 1,
    color: '#fff',
    textAlign: 'center',
    fontSize: 16,
    fontWeight: '700',
  },
  loader: {
    position: 'absolute',
    top: 60,
    left: 0,
    right: 0,
    alignItems: 'center',
  },
});

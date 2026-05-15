import React from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  FlatList,
  StatusBar,
  SafeAreaView,
} from 'react-native';
import { APPS } from './apps';

export default function HomeScreen({ navigation }) {
  const renderItem = ({ item }) => (
    <TouchableOpacity
      style={[styles.tile, { backgroundColor: item.color }]}
      activeOpacity={0.85}
      onPress={() =>
        navigation.navigate('WebView', {
          url: item.url,
          title: item.name,
        })
      }
    >
      <Text style={styles.initial}>{item.initial}</Text>
      <Text style={styles.tileLabel}>{item.name}</Text>
    </TouchableOpacity>
  );

  return (
    <SafeAreaView style={styles.safe}>
      <StatusBar barStyle="light-content" />
      <View style={styles.header}>
        <Text style={styles.title}>SuperApp</Text>
        <Text style={styles.subtitle}>All your social apps in one place</Text>
      </View>
      <FlatList
        data={APPS}
        keyExtractor={(i) => i.id}
        renderItem={renderItem}
        numColumns={2}
        contentContainerStyle={styles.grid}
        columnWrapperStyle={{ gap: 14 }}
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: '#0b1020' },
  header: { paddingHorizontal: 20, paddingTop: 16, paddingBottom: 8 },
  title: { color: '#fff', fontSize: 28, fontWeight: '800' },
  subtitle: { color: '#9aa3c7', marginTop: 4 },
  grid: { padding: 20, gap: 14 },
  tile: {
    flex: 1,
    aspectRatio: 1,
    borderRadius: 20,
    padding: 18,
    justifyContent: 'space-between',
    shadowColor: '#000',
    shadowOpacity: 0.25,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 6 },
    elevation: 4,
  },
  initial: {
    color: '#fff',
    fontSize: 44,
    fontWeight: '900',
    letterSpacing: -1,
  },
  tileLabel: { color: '#fff', fontSize: 16, fontWeight: '700' },
});

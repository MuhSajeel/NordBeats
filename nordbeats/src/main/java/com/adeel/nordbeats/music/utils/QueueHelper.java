/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.adeel.nordbeats.music.utils;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;

import com.adeel.nordbeats.music.VoiceSearchParams;
import com.adeel.nordbeats.music.model.MusicProvider;

import java.util.ArrayList;
import java.util.List;

import static com.adeel.nordbeats.music.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_GENRE;
import static com.adeel.nordbeats.music.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;

/**
 * Utility class to help on queue related tasks.
 */
public class QueueHelper {

    private static final String TAG = LogHelper.makeLogTag(QueueHelper.class);

    private static final int RANDOM_QUEUE_SIZE = 10;

    public static List<MediaSessionCompat.QueueItem> getPlayingQueue(String mediaId,
            MusicProvider musicProvider) {

        // extract the browsing hierarchy from the media ID:
        String[] hierarchy = MediaIDHelper.getHierarchy(mediaId);

        if (hierarchy.length != 2) {
            LogHelper.e(TAG, "Could not build a playing queue for this mediaId: ", mediaId);
            return null;
        }

        String categoryType = hierarchy[0];
        String categoryValue = hierarchy[1];
        LogHelper.d(TAG, "Creating playing queue for ", categoryType, ",  ", categoryValue);

        Iterable<MediaMetadataCompat> tracks = null;
        // This sample only supports genre and by_search category types.
        if (categoryType.equals(MEDIA_ID_MUSICS_BY_GENRE)) {
            tracks = musicProvider.getMusicsByGenre(categoryValue);
        } else if (categoryType.equals(MEDIA_ID_MUSICS_BY_SEARCH)) {
            tracks = musicProvider.searchMusicBySongTitle(categoryValue);
        }

        if (tracks == null) {
            LogHelper.e(TAG, "Unrecognized category type: ", categoryType, " for media ", mediaId);
            return null;
        }

        return convertToQueue(tracks, hierarchy[0], hierarchy[1]);
    }

    public static List<MediaSessionCompat.QueueItem> getPlayingQueueFromSearch(String query,
            Bundle queryParams, MusicProvider musicProvider) {

        LogHelper.d(TAG, "Creating playing queue for musics from search: ", query,
            " params=", queryParams);

        VoiceSearchParams params = new VoiceSearchParams(query, queryParams);

        LogHelper.d(TAG, "VoiceSearchParams: ", params);

        if (params.isAny) {
            // If isAny is true, we will play anything. This is app-dependent, and can be,
            // for example, favorite playlists, "I'm feeling lucky", most recent, etc.
            return getRandomQueue(musicProvider);
        }

        List<MediaMetadataCompat> result = null;
        if (params.isAlbumFocus) {
            result = musicProvider.searchMusicByAlbum(params.album);
        } else if (params.isGenreFocus) {
            result = musicProvider.getMusicsByGenre(params.genre);
        } else if (params.isArtistFocus) {
            result = musicProvider.searchMusicByArtist(params.artist);
        } else if (params.isSongFocus) {
            result = musicProvider.searchMusicBySongTitle(params.song);
        }

        // If there was no results using media focus parameter, we do an unstructured query.
        // This is useful when the user is searching for something that looks like an artist
        // to Google, for example, but is not. For example, a user searching for Madonna on
        // a PodCast application wouldn't get results if we only looked at the
        // Artist (podcast author). Then, we can instead do an unstructured search.
        if (params.isUnstructured || result == null || !result.iterator().hasNext()) {
            // To keep it simple for this example, we do unstructured searches on the
            // song title and genre only. A real world application could search
            // on other fields as well.
            result = musicProvider.searchMusicBySongTitle(query);
            if (result.isEmpty()) {
                result = musicProvider.searchMusicByGenre(query);
            }
        }

        return convertToQueue(result, MEDIA_ID_MUSICS_BY_SEARCH, query);
    }


    public static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue,
             String mediaId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (mediaId.equals(item.getDescription().getMediaId())) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue,
             long queueId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (queueId == item.getQueueId()) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static List<MediaSessionCompat.QueueItem> convertToQueue(
            Iterable<MediaMetadataCompat> tracks, String... categories) {
        List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        int count = 0;
        for (MediaMetadataCompat track : tracks) {

            // We create a hierarchy-aware mediaID, so we know what the queue is about by looking
            // at the QueueItem media IDs.
            String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                    track.getDescription().getMediaId(), categories);

            MediaMetadataCompat trackCopy = new MediaMetadataCompat.Builder(track)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                    .build();

            // We don't expect queues to change after created, so we use the item index as the
            // queueId. Any other number unique in the queue would work.
            MediaSessionCompat.QueueItem item = new MediaSessionCompat.QueueItem(
                    trackCopy.getDescription(), count++);
            queue.add(item);
        }
        return queue;

    }

    /**
     * Create a random queue with at most {@link #RANDOM_QUEUE_SIZE} elements.
     *
     * @param musicProvider the provider used for fetching music.
     * @return list containing {@link MediaSessionCompat.QueueItem}'s
     */
    public static List<MediaSessionCompat.QueueItem> getRandomQueue(MusicProvider musicProvider) {
        List<MediaMetadataCompat> result = new ArrayList<>(RANDOM_QUEUE_SIZE);
        Iterable<MediaMetadataCompat> shuffled = musicProvider.getShuffledMusic();
        for (MediaMetadataCompat metadata: shuffled) {
            if (result.size() == RANDOM_QUEUE_SIZE) {
                break;
            }
            result.add(metadata);
        }
        LogHelper.d(TAG, "getRandomQueue: result.size=", result.size());

        return convertToQueue(result, MEDIA_ID_MUSICS_BY_SEARCH, "random");
    }

    public static boolean isIndexPlayable(int index, List<MediaSessionCompat.QueueItem> queue) {
        return (queue != null && index >= 0 && index < queue.size());
    }

    /**
     * Determine if two queues contain identical media id's in order.
     *
     * @param list1 containing {@link MediaSessionCompat.QueueItem}'s
     * @param list2 containing {@link MediaSessionCompat.QueueItem}'s
     * @return boolean indicating whether the queue's match
     */
    public static boolean equals(List<MediaSessionCompat.QueueItem> list1,
                                 List<MediaSessionCompat.QueueItem> list2) {
        if (list1 == list2) {
            return true;
        }
        if (list1 == null || list2 == null) {
            return false;
        }
        if (list1.size() != list2.size()) {
            return false;
        }
        for (int i=0; i<list1.size(); i++) {
            if (list1.get(i).getQueueId() != list2.get(i).getQueueId()) {
                return false;
            }
            if (!TextUtils.equals(list1.get(i).getDescription().getMediaId(),
                    list2.get(i).getDescription().getMediaId())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determine if queue item matches the currently playing queue item
     *
     * @param context for retrieving the {@link MediaControllerCompat}
     * @param queueItem to compare to currently playing {@link MediaSessionCompat.QueueItem}
     * @return boolean indicating whether queue item matches currently playing queue item
     */
    public static boolean isQueueItemPlaying(Activity context,
                                             MediaSessionCompat.QueueItem queueItem) {
        // Queue item is considered to be playing or paused based on both the controller's
        // current media id and the controller's active queue item id
        MediaControllerCompat controller = MediaControllerCompat.getMediaController(context);
        if (controller != null && controller.getPlaybackState() != null) {
            long currentPlayingQueueId = controller.getPlaybackState().getActiveQueueItemId();
            String currentPlayingMediaId = controller.getMetadata().getDescription()
                    .getMediaId();
            String itemMusicId = MediaIDHelper.extractMusicIDFromMediaID(
                    queueItem.getDescription().getMediaId());
            if (queueItem.getQueueId() == currentPlayingQueueId
                    && currentPlayingMediaId != null
                    && TextUtils.equals(currentPlayingMediaId, itemMusicId)) {
                return true;
            }
        }
        return false;
    }
}

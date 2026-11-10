/*
 * Copyright (C) 2024-2026 Lunaris AOSP
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
 * limitations under the License
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.android.dialer.expressive

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.android.dialer.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun NumberActionsDialog(
    name: String, number: String, starred: Boolean, saved: Boolean,
    onDismiss: () -> Unit, onCall: () -> Unit, onVideo: () -> Unit, onMessage: () -> Unit,
    onWhatsApp: () -> Unit,
    onFavorite: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit, onEdit: () -> Unit,
    onPhoto: () -> Unit, onRemovePhoto: () -> Unit,
    onBackground: () -> Unit = {}, onRemoveBackground: () -> Unit = {},
    onRecordings: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember(context) { DialerRepository(context) }
    val scope = rememberCoroutineScope()
    var blockState by remember(number) { mutableStateOf<NumberBlockState?>(null) }
    var confirmBlock by remember(number) { mutableStateOf(false) }
    var changingBlock by remember(number) { mutableStateOf(false) }
    LaunchedEffect(number) {
        blockState = try { repository.blockState(number) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { NumberBlockState(false, false) }
    }
    val photoState = rememberCallerPhotoState(number, 512)
    // I still keep here photo in my wallet
    val photo = photoState.image
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.widthIn(max = 400.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (photo != null) Image(photo, null, Modifier.size(96.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else Surface(Modifier.size(96.dp), shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(name.trim().firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.displaySmallEmphasized)
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmallEmphasized,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (name != number) Text(number, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberAction(DialerSymbol.Phone, stringResource(R.string.expressive_call), Modifier.weight(1f), onCall)
                    NumberAction(DialerSymbol.Message, stringResource(R.string.expressive_message), Modifier.weight(1f), onMessage)
                }
                NumberAction(DialerSymbol.Message, stringResource(R.string.expressive_whatsapp_message),
                    Modifier.fillMaxWidth(), onWhatsApp, enabled = number.isNotBlank())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberAction(DialerSymbol.Video, stringResource(R.string.expressive_video_call), Modifier.weight(1f), onVideo)
                    NumberAction(if (saved) DialerSymbol.Star else DialerSymbol.Add,
                        stringResource(if (!saved) R.string.expressive_save else if (starred) R.string.expressive_unfavorite else R.string.expressive_favorite),
                        Modifier.weight(1f), onFavorite)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberAction(DialerSymbol.Record, stringResource(R.string.expressive_call_recording),
                        Modifier.weight(1f), onRecordings, enabled = number.isNotBlank())
                    NumberAction(if (blockState?.blocked == true) DialerSymbol.Phone else DialerSymbol.Block,
                        stringResource(if (blockState?.blocked == true) R.string.expressive_unblock_number else R.string.expressive_block_number),
                        Modifier.weight(1f), { confirmBlock = true },
                        enabled = blockState?.available == true && !changingBlock)
                }
                NumberAction(DialerSymbol.Block, stringResource(R.string.expressive_report_spam),
                    Modifier.fillMaxWidth(), { showCarrierBlockDialog(context, number, true) },
                    enabled = number.isNotBlank())
                if (blockState?.available == false) Text(stringResource(R.string.expressive_block_unavailable),
                    Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilledIconButton(onClick = onCopy, modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                        Symbol(DialerSymbol.Copy, stringResource(R.string.expressive_copy_number))
                    }
                    FilledIconButton(onClick = onShare, modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.surface)) {
                        Symbol(DialerSymbol.Share, stringResource(R.string.expressive_share_number))
                    }
                    OutlinedIconButton(onClick = onEdit, modifier = Modifier.size(56.dp)) {
                        Symbol(DialerSymbol.Edit, stringResource(if (saved) R.string.expressive_edit_contact else R.string.expressive_create_contact))
                    }
                }
                NumberAction(DialerSymbol.Photo, stringResource(if (photo == null) R.string.expressive_set_caller_photo
                    else R.string.expressive_change_caller_photo), Modifier.fillMaxWidth(), onPhoto)
                if (photoState.hasOverride) TextButton(onClick = onRemovePhoto, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.expressive_remove_caller_photo))
                }
                val contactBg = rememberContactBackground(number)
                NumberAction(DialerSymbol.Video, stringResource(if (contactBg == null) R.string.expressive_set_contact_background
                    else R.string.expressive_change_contact_background), Modifier.fillMaxWidth(), onBackground)
                if (contactBg != null) TextButton(onClick = onRemoveBackground, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.expressive_remove_contact_background))
                }
                if (onDelete != null) TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.expressive_delete_history), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmBlock) {
        val block = blockState?.blocked != true
        AlertDialog(onDismissRequest = { if (!changingBlock) confirmBlock = false },
            title = { Text(stringResource(if (block) R.string.expressive_block_number else R.string.expressive_unblock_number)) },
            text = { Text(stringResource(if (block) R.string.expressive_block_number_body else R.string.expressive_unblock_number_body, number)) },
            confirmButton = {
                TextButton(enabled = !changingBlock, onClick = {
                    changingBlock = true
                    scope.launch {
                        try {
                            blockState = repository.setBlocked(number, block)
                            confirmBlock = false
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) {
                            Toast.makeText(context, R.string.expressive_action_failed, Toast.LENGTH_LONG).show()
                        } finally { changingBlock = false }
                    }
                }) { Text(stringResource(if (block) R.string.expressive_block_number else R.string.expressive_unblock_number)) }
            },
            dismissButton = {
                TextButton(enabled = !changingBlock, onClick = { confirmBlock = false }) {
                    Text(stringResource(R.string.expressive_cancel))
                }
            })
    }
}

@Composable
private fun NumberAction(icon: DialerSymbol, label: String, modifier: Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    ExpressiveTile(onClick, modifier, enabled = enabled, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Symbol(icon, modifier = Modifier.size(20.dp))
        Text(label, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge)
    }
}

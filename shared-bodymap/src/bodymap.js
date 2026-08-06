/*
 * P1 Body Map — PainDiary-style 3D body region picker.
 * Monochrome clinical mannequin, fully offline, Three.js r149 (vendored).
 *
 * Native bridge (both platforms):
 *   JS -> native : window.AndroidBodyMap.onEvent(json)                    (Android)
 *                  window.webkit.messageHandlers.bodymap.postMessage(json) (iOS)
 *   native -> JS : bodymapSetRegions(['head', ...])
 *                  bodymapReset()
 *                  bodymapSetView('front' | 'back')
 *                  bodymapGetRegions() -> array of region ids
 *
 * Events sent to native: { type: 'ready', webgl: bool } and
 *   { type: 'selection', regions: [...ids], labels: [...labels] }
 */
(function () {
  'use strict';

  // ── Regions ────────────────────────────────────────────────────────────

  var REGION_LABELS = {
    head: 'Head',
    neck: 'Neck',
    chest: 'Chest',
    upper_back: 'Upper back',
    abdomen: 'Abdomen',
    lower_back: 'Lower back',
    pelvis: 'Pelvis',
    buttocks: 'Buttocks',
    left_shoulder: 'Left shoulder',
    right_shoulder: 'Right shoulder',
    left_upper_arm: 'Left upper arm',
    right_upper_arm: 'Right upper arm',
    left_forearm: 'Left forearm',
    right_forearm: 'Right forearm',
    left_hand: 'Left hand',
    right_hand: 'Right hand',
    left_thigh: 'Left thigh',
    right_thigh: 'Right thigh',
    left_calf: 'Left calf',
    right_calf: 'Right calf',
    left_foot: 'Left foot',
    right_foot: 'Right foot'
  };

  // Strict monochrome palette (matches the app theme).
  var COLOR_BG = 0xffffff;
  var COLOR_BODY = 0xaaaaaa;
  var COLOR_SELECTED = 0x111111;
  var COLOR_FLOOR = 0xe0e0e0;

  var selected = new Set();
  var hitMeshes = [];
  var fallbackButtons = {};
  var sceneSetYaw = null;

  function labelFor(id) {
    return REGION_LABELS[id] || id;
  }

  // ── Native bridge ──────────────────────────────────────────────────────

  window.onerror = function (msg, src, line) {
    try {
      notifyNative({ type: 'error', message: String(msg), at: String(src) + ':' + line });
    } catch (e) { /* ignore */ }
    return false;
  };

  function notifyNative(payload) {
    var json = JSON.stringify(payload);
    try {
      if (window.AndroidBodyMap && typeof window.AndroidBodyMap.onEvent === 'function') {
        window.AndroidBodyMap.onEvent(json);
        return;
      }
      if (
        window.webkit &&
        window.webkit.messageHandlers &&
        window.webkit.messageHandlers.bodymap
      ) {
        window.webkit.messageHandlers.bodymap.postMessage(json);
      }
    } catch (e) {
      /* bridge absent (desktop preview) — ignore */
    }
  }

  function notifySelection() {
    var regions = Array.from(selected);
    notifyNative({
      type: 'selection',
      regions: regions,
      labels: regions.map(labelFor)
    });
  }

  function toggleRegion(id) {
    if (selected.has(id)) selected.delete(id);
    else selected.add(id);
    refreshColors();
    notifySelection();
  }

  // ── Body construction ──────────────────────────────────────────────────

  // Materials are created lazily so the script also parses/loads on
  // devices where THREE (or WebGL) is unavailable — the fallback list
  // then works without touching THREE at all.
  var bodyMaterial = null;
  var selectedMaterial = null;

  function initMaterials() {
    bodyMaterial = new THREE.MeshStandardMaterial({
      color: COLOR_BODY,
      roughness: 0.5,
      metalness: 0.0
    });
    selectedMaterial = new THREE.MeshStandardMaterial({
      color: COLOR_SELECTED,
      roughness: 0.35,
      metalness: 0.0
    });
  }

  function registerMesh(mesh, regionId) {
    mesh.userData.regionId = regionId;
    hitMeshes.push(mesh);
    return mesh;
  }

  function addSphere(group, regionId, r, sx, sy, sz, x, y, z) {
    var geo = new THREE.SphereGeometry(r, 28, 22);
    var mesh = new THREE.Mesh(geo, bodyMaterial);
    mesh.scale.set(sx, sy, sz);
    mesh.position.set(x, y, z);
    group.add(registerMesh(mesh, regionId));
    return mesh;
  }

  // Half-shell sphere: front = phi [0, PI] (z >= 0), back = phi [PI, 2PI].
  function addHalfSphere(group, regionId, front, r, sx, sy, sz, x, y, z) {
    var geo = new THREE.SphereGeometry(r, 28, 22, front ? 0 : Math.PI, Math.PI);
    var mesh = new THREE.Mesh(geo, bodyMaterial);
    mesh.scale.set(sx, sy, sz);
    mesh.position.set(x, y, z);
    group.add(registerMesh(mesh, regionId));
    return mesh;
  }

  function addCapsule(group, regionId, r, len, x, y, z, rotZ) {
    var geo = new THREE.CapsuleGeometry(r, len, 6, 20);
    var mesh = new THREE.Mesh(geo, bodyMaterial);
    mesh.position.set(x, y, z);
    if (rotZ) mesh.rotation.z = rotZ;
    group.add(registerMesh(mesh, regionId));
    return mesh;
  }

  function buildBody() {
    var g = new THREE.Group();

    // Feet (body faces +z).
    addSphere(g, 'left_foot', 0.17, 1.0, 0.55, 1.9, 0.25, 0.10, 0.10);
    addSphere(g, 'right_foot', 0.17, 1.0, 0.55, 1.9, -0.25, 0.10, 0.10);

    // Calves and thighs.
    addCapsule(g, 'left_calf', 0.155, 0.72, 0.24, 0.78, 0);
    addCapsule(g, 'right_calf', 0.155, 0.72, -0.24, 0.78, 0);
    addCapsule(g, 'left_thigh', 0.20, 0.78, 0.25, 1.85, 0);
    addCapsule(g, 'right_thigh', 0.20, 0.78, -0.25, 1.85, 0);

    // Pelvis / buttocks, abdomen / lower back, chest / upper back:
    // front and back are separate shells so each side colours independently.
    // Shells overlap generously so the torso reads as one continuous volume.
    addHalfSphere(g, 'pelvis', true, 0.52, 1.06, 0.88, 0.74, 0, 2.68, 0);
    addHalfSphere(g, 'buttocks', false, 0.52, 1.06, 0.88, 0.74, 0, 2.68, 0);
    addHalfSphere(g, 'abdomen', true, 0.54, 1.0, 0.98, 0.70, 0, 3.30, 0);
    addHalfSphere(g, 'lower_back', false, 0.54, 1.0, 0.98, 0.70, 0, 3.30, 0);
    addHalfSphere(g, 'chest', true, 0.60, 1.06, 1.06, 0.70, 0, 3.96, 0);
    addHalfSphere(g, 'upper_back', false, 0.60, 1.06, 1.06, 0.70, 0, 3.96, 0);

    // Neck and head.
    var neckGeo = new THREE.CylinderGeometry(0.17, 0.19, 0.46, 20);
    var neck = new THREE.Mesh(neckGeo, bodyMaterial);
    neck.position.set(0, 4.64, 0);
    g.add(registerMesh(neck, 'neck'));
    addSphere(g, 'head', 0.41, 0.92, 1.14, 0.98, 0, 5.22, 0);

    // Shoulders.
    addSphere(g, 'left_shoulder', 0.20, 1, 1, 1, 0.64, 4.30, 0);
    addSphere(g, 'right_shoulder', 0.20, 1, 1, 1, -0.64, 4.30, 0);

    // Arms in a relaxed A-pose, slightly away from the torso.
    addCapsule(g, 'left_upper_arm', 0.135, 0.55, 0.80, 3.82, 0, 0.14);
    addCapsule(g, 'right_upper_arm', 0.135, 0.55, -0.80, 3.82, 0, -0.14);
    addCapsule(g, 'left_forearm', 0.115, 0.55, 0.92, 3.02, 0.02, 0.10);
    addCapsule(g, 'right_forearm', 0.115, 0.55, -0.92, 3.02, 0.02, -0.10);
    addSphere(g, 'left_hand', 0.165, 0.75, 1.2, 0.55, 0.97, 2.50, 0.04);
    addSphere(g, 'right_hand', 0.165, 0.75, 1.2, 0.55, -0.97, 2.50, 0.04);

    return g;
  }

  // ── Selection rendering ────────────────────────────────────────────────

  function refreshColors() {
    if (bodyMaterial && selectedMaterial) {
      for (var i = 0; i < hitMeshes.length; i++) {
        var mesh = hitMeshes[i];
        mesh.material = selected.has(mesh.userData.regionId) ? selectedMaterial : bodyMaterial;
      }
    }
    Object.keys(fallbackButtons).forEach(function (id) {
      var b = fallbackButtons[id];
      var on = selected.has(id);
      b.style.background = on ? '#000' : '#fff';
      b.style.color = on ? '#fff' : '#000';
    });
  }

  // ── Fallback (no WebGL): accessible region list ────────────────────────

  function buildFallback(container) {
    var wrap = document.createElement('div');
    wrap.style.cssText =
      'display:flex;flex-wrap:wrap;gap:8px;padding:16px;background:#fff;' +
      'font-family:-apple-system,Roboto,sans-serif;align-content:flex-start;' +
      'height:100%;box-sizing:border-box;overflow:auto;';
    var note = document.createElement('div');
    note.textContent = '3D unavailable on this device — tap areas to select:';
    note.style.cssText = 'width:100%;font-size:12px;color:#737373;margin-bottom:4px;';
    wrap.appendChild(note);

    Object.keys(REGION_LABELS).forEach(function (id) {
      var b = document.createElement('button');
      b.textContent = REGION_LABELS[id];
      b.style.cssText =
        'border:1px solid #e5e5e5;background:#fff;color:#000;border-radius:20px;' +
        'padding:8px 14px;font-size:13px;';
      b.addEventListener('click', function () {
        toggleRegion(id);
      });
      fallbackButtons[id] = b;
      wrap.appendChild(b);
    });
    container.appendChild(wrap);
  }

  // ── Main 3D scene ──────────────────────────────────────────────────────

  function initScene(container) {
    var renderer;
    try {
      initMaterials();
      // preserveDrawingBuffer: required if we ever blit; also avoids blank
      // frames on Android WebView after present. alpha:false = solid white bg.
      renderer = new THREE.WebGLRenderer({
        antialias: true,
        alpha: false,
        preserveDrawingBuffer: true,
        powerPreference: 'high-performance'
      });
    } catch (e) {
      buildFallback(container);
      notifyNative({ type: 'ready', webgl: false });
      return;
    }

    var scene = new THREE.Scene();
    scene.background = new THREE.Color(COLOR_BG);
    var camera = new THREE.PerspectiveCamera(40, 1, 0.1, 100);

    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.outputEncoding = THREE.sRGBEncoding;
    // Draw WebGL directly (Chrome + WebView). Do NOT use display:none + 2D
    // blit: hidden WebGL canvases often skip rendering on Android WebView,
    // and LAYER_TYPE_SOFTWARE + blit produced a permanent white screen.
    var glCanvas = renderer.domElement;
    glCanvas.style.display = 'block';
    glCanvas.style.width = '100%';
    glCanvas.style.height = '100%';
    glCanvas.style.touchAction = 'none';
    container.appendChild(glCanvas);

    // Soft clinical lighting — low fill so the directional light models the
    // volumes clearly (dark body on white background, mobile-friendly).
    scene.add(new THREE.HemisphereLight(0xffffff, 0xaaaaaa, 0.35));
    var key = new THREE.DirectionalLight(0xffffff, 0.75);
    key.position.set(1.4, 5.0, 4.2);
    scene.add(key);
    var fill = new THREE.DirectionalLight(0xffffff, 0.20);
    fill.position.set(-3, 2, -2.5);
    scene.add(fill);

    var rig = new THREE.Group();
    var body = buildBody();
    rig.add(body);
    scene.add(rig);

    var floor = new THREE.Mesh(
      new THREE.CircleGeometry(1.45, 48),
      new THREE.MeshBasicMaterial({ color: COLOR_FLOOR })
    );
    floor.rotation.x = -Math.PI / 2;
    floor.position.y = 0.001;
    scene.add(floor);

    // Camera auto-framing from the body bounding box.
    function frameCamera() {
      var w = container.clientWidth || 1;
      var h = container.clientHeight || 1;
      renderer.setSize(w, h, false);
      glCanvas.style.width = w + 'px';
      glCanvas.style.height = h + 'px';
      camera.aspect = w / h;
      camera.updateProjectionMatrix();

      var bbox = new THREE.Box3().setFromObject(body);
      var center = bbox.getCenter(new THREE.Vector3());
      var size = bbox.getSize(new THREE.Vector3());
      var halfFov = (camera.fov * Math.PI) / 360;
      var distY = size.y / 2 / Math.tan(halfFov);
      var distX = size.x / 2 / (Math.tan(halfFov) * camera.aspect);
      var dist = Math.max(distX, distY) * 1.18 + size.z / 2;
      camera.position.set(center.x, center.y, center.z + dist);
      camera.lookAt(center);
    }
    // In-app WebViews (bottom sheets, animated containers) often have a
    // zero-height layout viewport when the page boots, which also collapses
    // height:100% containers. Size the container explicitly from the real
    // window size and reframe until it stabilises.
    function syncSize() {
      var w = window.innerWidth;
      var h = window.innerHeight;
      if (w > 0 && h > 0) {
        container.style.width = w + 'px';
        container.style.height = h + 'px';
        frameCamera();
        return true;
      }
      return false;
    }
    syncSize();
    window.addEventListener('resize', syncSize);
    var tries = 0;
    var timer = setInterval(function () {
      if (++tries > 40) clearInterval(timer);
      else if (syncSize() && tries > 4) clearInterval(timer);
    }, 250);

    // Interaction: drag to rotate, tap to select.
    var yaw = 0;
    var targetYaw = 0;
    var pitch = 0;
    var targetPitch = 0;
    var lastX = 0;
    var lastY = 0;
    var downX = 0;
    var downY = 0;
    var downTime = 0;
    var dragging = false;

    sceneSetYaw = function (v) {
      targetYaw = v;
    };

    var raycaster = new THREE.Raycaster();
    var ndc = new THREE.Vector2();

    function onPointerDown(e) {
      lastX = downX = e.clientX;
      lastY = downY = e.clientY;
      downTime = Date.now();
      dragging = true;
    }

    function onPointerMove(e) {
      if (!dragging) return;
      var dx = e.clientX - lastX;
      var dy = e.clientY - lastY;
      lastX = e.clientX;
      lastY = e.clientY;
      targetYaw += dx * 0.009;
      targetPitch = Math.max(-0.12, Math.min(0.35, targetPitch + dy * 0.004));
    }

    function onPointerUp(e) {
      if (!dragging) return;
      dragging = false;
      var moved = Math.abs(e.clientX - downX) + Math.abs(e.clientY - downY);
      if (moved > 8 || Date.now() - downTime > 500) return; // it was a drag

      var rect = glCanvas.getBoundingClientRect();
      if (rect.width < 1 || rect.height < 1) return;
      ndc.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
      ndc.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
      raycaster.setFromCamera(ndc, camera);
      var hits = raycaster.intersectObjects(hitMeshes, false);
      if (!hits.length) return;
      toggleRegion(hits[0].object.userData.regionId);
    }

    if (window.PointerEvent) {
      glCanvas.addEventListener('pointerdown', onPointerDown);
      glCanvas.addEventListener('pointermove', onPointerMove);
      glCanvas.addEventListener('pointerup', onPointerUp);
      glCanvas.addEventListener('pointercancel', function () {
        dragging = false;
      });
    } else {
      glCanvas.addEventListener('touchstart', function (e) {
        if (e.touches.length) onPointerDown(e.touches[0]);
      }, { passive: true });
      glCanvas.addEventListener('touchmove', function (e) {
        if (e.touches.length) onPointerMove(e.touches[0]);
        e.preventDefault();
      }, { passive: false });
      glCanvas.addEventListener('touchend', function (e) {
        if (e.changedTouches.length) onPointerUp(e.changedTouches[0]);
      });
    }

    var firstRenderNotified = false;
    function animate() {
      requestAnimationFrame(animate);
      yaw += (targetYaw - yaw) * 0.16;
      pitch += (targetPitch - pitch) * 0.16;
      rig.rotation.y = yaw;
      rig.rotation.x = pitch;
      renderer.render(scene, camera);
      if (!firstRenderNotified) {
        firstRenderNotified = true;
        notifyNative({
          type: 'render',
          canvasWidth: glCanvas.width,
          canvasHeight: glCanvas.height,
          cssWidth: container.clientWidth,
          cssHeight: container.clientHeight,
          dpr: window.devicePixelRatio || 1
        });
      }
    }
    animate();

    window.__bodymapScene = { renderer: renderer, scene: scene, camera: camera };
    notifyNative({ type: 'ready', webgl: true });
  }

  // ── Public API (native -> JS) ──────────────────────────────────────────

  window.bodymapSetRegions = function (ids) {
    selected = new Set(ids || []);
    refreshColors();
    notifySelection();
  };

  window.bodymapReset = function () {
    selected.clear();
    refreshColors();
    notifySelection();
  };

  window.bodymapSetView = function (side) {
    if (sceneSetYaw) sceneSetYaw(side === 'back' ? Math.PI : 0);
  };

  window.bodymapGetRegions = function () {
    return Array.from(selected);
  };

  // Debug probe: renders once synchronously and samples pixels in the same
  // task (the drawing buffer is invalid after compositing), so native can
  // tell whether the scene actually draws regardless of compositing.
  window.bodymapProbe = function () {
    try {
      var ctx = window.__bodymapScene;
      if (!ctx) return 'no-scene';
      ctx.renderer.render(ctx.scene, ctx.camera);
      var gl = ctx.renderer.getContext();
      var w = gl.drawingBufferWidth;
      var h = gl.drawingBufferHeight;
      var px = new Uint8Array(4);
      var sum = 0;
      var samples = 0;
      for (var i = 1; i <= 9; i++) {
        for (var j = 1; j <= 9; j++) {
          gl.readPixels(
            Math.floor((w * i) / 10), Math.floor((h * j) / 10),
            1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px
          );
          sum += (px[0] + px[1] + px[2]) / 3;
          samples++;
        }
      }
      return 'avg=' + Math.round(sum / samples) + ' buf=' + w + 'x' + h;
    } catch (e) {
      return 'probe-error: ' + e;
    }
  };

  // ── Boot ───────────────────────────────────────────────────────────────

  function boot() {
    var container = document.getElementById('bodymap');
    if (!container) return;
    if (typeof THREE === 'undefined') {
      buildFallback(container);
      notifyNative({ type: 'ready', webgl: false });
      return;
    }
    initScene(container);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();

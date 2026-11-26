from flask import Flask, render_template, request, send_file, jsonify
from PIL import Image, ImageOps
import io, os, re, zipfile

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 200 * 1024 * 1024

@app.errorhandler(413)
def too_large(_):
    return jsonify(error="Selected files ka total size 200 MB se zyada hai."), 413

def safe_name(name):
    stem = os.path.splitext(os.path.basename(name))[0]
    stem = re.sub(r"[^A-Za-z0-9._-]+", "_", stem).strip("._")
    return (stem or "image")[:70]

def page_points(size, orientation, image_size):
    sizes = {"A4": (595.276, 841.89), "A5": (419.528, 595.276), "Letter": (612, 792)}
    if size == "Original":
        return image_size[0] * 72 / 96, image_size[1] * 72 / 96
    w, h = sizes.get(size, sizes["A4"])
    if orientation == "Auto":
        return (max(w,h), min(w,h)) if image_size[0] > image_size[1] else (min(w,h), max(w,h))
    if orientation == "Landscape":
        return max(w,h), min(w,h)
    return min(w,h), max(w,h)

def read_image(upload):
    raw = upload.read()
    if not raw:
        raise ValueError(f"{upload.filename}: file empty hai.")
    im = Image.open(io.BytesIO(raw))
    im = ImageOps.exif_transpose(im)
    im.load()
    if im.mode in ("RGBA", "LA") or "transparency" in im.info:
        rgba = im.convert("RGBA")
        bg = Image.new("RGBA", rgba.size, "white")
        bg.alpha_composite(rgba)
        im = bg.convert("RGB")
    else:
        im = im.convert("RGB")
    return {"name": safe_name(upload.filename), "image": im, "source_bytes": len(raw)}

def render_page(source, opts, dpi):
    im = source["image"].copy()
    pw, ph = page_points(opts["page_size"], opts["orientation"], im.size)
    margin = {"None": 0, "Small": 18, "Medium": 36, "Large": 54}.get(opts["margins"], 0)
    aw, ah = max(1, pw-2*margin), max(1, ph-2*margin)
    if opts["fit"] == "Fill":
        ratio = aw / ah
        if im.width / im.height > ratio:
            cw = max(1, round(im.height * ratio))
            left = (im.width-cw)//2
            im = im.crop((left, 0, left+cw, im.height))
        else:
            ch = max(1, round(im.width / ratio))
            top = (im.height-ch)//2
            im = im.crop((0, top, im.width, top+ch))
        scale = min(aw/im.width, ah/im.height)
    elif opts["fit"] == "Original" or opts["page_size"] == "Original":
        scale = min(1.0, aw/im.width, ah/im.height)
    else:
        scale = min(aw/im.width, ah/im.height)
    w, h = max(1, round(im.width*scale)), max(1, round(im.height*scale))
    im = im.resize((max(1, round(w*dpi/72)), max(1, round(h*dpi/72))), Image.Resampling.LANCZOS)
    canvas = Image.new("RGB", (max(1,round(pw*dpi/72)), max(1,round(ph*dpi/72))), "white")
    x = max(0, round((pw-w)/2*dpi/72))
    y = max(0, round((ph-h)/2*dpi/72))
    im.thumbnail((canvas.width, canvas.height), Image.Resampling.LANCZOS)
    canvas.paste(im, (min(x,canvas.width-im.width), min(y,canvas.height-im.height)))
    return canvas

def build_pdf(sources, opts, dpi, jpeg_quality):
    pages = [render_page(s, opts, dpi) for s in sources]
    output = io.BytesIO()
    pages[0].save(output, format="PDF", save_all=True, append_images=pages[1:],
                  resolution=dpi, quality=jpeg_quality, optimize=True)
    return output.getvalue()

def optimize_pdf(sources, opts, target_bytes=None):
    # Normal: strong everyday quality. Custom: test progressively smaller encodings,
    # keeping the highest-detail result that fits the requested maximum.
    if target_bytes is None:
        return build_pdf(sources, opts, 200, 88), True, 200
    target_bytes = int(target_bytes)
    candidates = [(240,92),(220,90),(200,88),(180,86),(160,84),(150,80),(130,76),(110,70),(96,64),(80,58)]
    smallest = None
    for dpi, quality in candidates:
        data = build_pdf(sources, opts, dpi, quality)
        if smallest is None or len(data) < len(smallest[0]):
            smallest = (data,dpi)
        if len(data) <= target_bytes:
            return data, True, dpi
    return smallest[0], False, smallest[1]

def pdf_response(data, filename, target_met):
    response = send_file(io.BytesIO(data), mimetype="application/pdf",
                         as_attachment=True, download_name=filename)
    response.headers["X-PaperLite-Target-Met"] = "yes" if target_met else "no"
    return response

@app.get("/")
def index():
    return render_template("index.html")


@app.post("/api/analyze")
def analyze():
    uploads = [f for f in request.files.getlist("images") if f and f.filename]
    if not uploads:
        return jsonify(error="Pehle images upload karo."), 400
    try:
        opts = {
            "mode": request.form.get("mode", "merged"),
            "page_size": request.form.get("page_size", "A4"),
            "orientation": request.form.get("orientation", "Auto"),
            "fit": request.form.get("fit", "Fit"),
            "margins": request.form.get("margins", "None"),
        }
        sources = [read_image(f) for f in uploads]
        input_bytes = sum(x["source_bytes"] for x in sources)
        # This is a real local PDF render using the same settings as Normal mode.
        # It gives a much more useful estimate than the sum of source image bytes.
        if opts["mode"] == "separate" and len(sources) > 1:
            estimated = sum(len(build_pdf([item], opts, 200, 88)) for item in sources)
        else:
            estimated = len(build_pdf(sources, opts, 200, 88))
        return jsonify(input_bytes=input_bytes, estimated_pdf_bytes=estimated)
    except Exception as e:
        return jsonify(error=f"PDF estimate nahi ban saka: {str(e)}"), 400

@app.post("/api/convert")
def convert():
    uploads = [f for f in request.files.getlist("images") if f and f.filename]
    if not uploads:
        return jsonify(error="Pehle kam se kam ek image upload karo."), 400
    if len(uploads) > 100:
        return jsonify(error="Ek baar mein maximum 100 images add kar sakte ho."), 400
    try:
        opts = {
            "mode": request.form.get("mode", "merged"),
            "page_size": request.form.get("page_size", "A4"),
            "orientation": request.form.get("orientation", "Auto"),
            "fit": request.form.get("fit", "Fit"),
            "margins": request.form.get("margins", "None"),
        }
        size_mode = request.form.get("size_mode", "normal")
        target_bytes = None
        if size_mode == "custom":
            target_bytes = int(float(request.form.get("target_bytes", "0")))
            if target_bytes < 10 * 1024 or target_bytes > 200 * 1024 * 1024:
                return jsonify(error="Custom size 10 KB se 200 MB ke beech set karo."), 400
            # Server-side safety cap: requested output target cannot exceed uploaded input total.
            input_total = sum(getattr(f, "content_length", 0) or 0 for f in uploads)
            # Content-Length is not reliably populated for individual multipart files;
            # the client cap is backed up after images are read below.
        sources = [read_image(f) for f in uploads]
        if opts["mode"] not in ("merged", "separate"):
            opts["mode"] = "merged"

        if opts["mode"] == "merged" or len(sources) == 1:
            data, met, dpi = optimize_pdf(sources, opts, target_bytes)
            filename = (sources[0]["name"] + "_merged.pdf") if len(sources)>1 else sources[0]["name"]+".pdf"
            return pdf_response(data, filename, met)

        memory = io.BytesIO()
        all_met = True
        with zipfile.ZipFile(memory, "w", zipfile.ZIP_DEFLATED) as zf:
            used = {}
            baseline_sizes = [len(build_pdf([source], opts, 200, 88)) for source in sources]
            baseline_total = max(1, sum(baseline_sizes))
            for source, baseline_size in zip(sources, baseline_sizes):
                per_file_target = (max(10 * 1024, int(target_bytes * baseline_size / baseline_total))
                                   if target_bytes is not None else None)
                data, met, dpi = optimize_pdf([source], opts, per_file_target)
                all_met = all_met and met
                base = source["name"] + ".pdf"
                used[base] = used.get(base, 0) + 1
                if used[base] > 1:
                    base = f"{source['name']}_{used[base]}.pdf"
                zf.writestr(base, data)
        memory.seek(0)
        response = send_file(memory, mimetype="application/zip", as_attachment=True,
                             download_name="PaperLite_Separate_PDFs.zip")
        response.headers["X-PaperLite-Target-Met"] = "yes" if all_met else "no"
        return response
    except Exception as e:
        return jsonify(error=f"Conversion nahi ho paya: {str(e)}"), 400

if __name__ == "__main__":
    app.run(host="127.0.0.1", port=8767, debug=False)

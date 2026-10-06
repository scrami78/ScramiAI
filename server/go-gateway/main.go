package main

import (
	"bytes"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"os"
	"strings"
	"time"
)

type Gateway struct {
	AIBase string
	Client *http.Client
}

func (g *Gateway) proxy(path string, w http.ResponseWriter, r *http.Request) {
	body, err := io.ReadAll(io.LimitReader(r.Body, 2<<20))
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadRequest)
		return
	}
	req, err := http.NewRequestWithContext(r.Context(), http.MethodPost, g.AIBase+path, bytes.NewReader(body))
	if err != nil {
		http.Error(w, err.Error(), http.StatusBadGateway)
		return
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := g.Client.Do(req)
	if err != nil {
		http.Error(w, "S.AI Core unavailable: "+err.Error(), http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()
	w.Header().Set("Content-Type", resp.Header.Get("Content-Type"))
	w.WriteHeader(resp.StatusCode)
	_, _ = io.Copy(w, resp.Body)
}

func main() {
	aiBase := strings.TrimRight(os.Getenv("SAI_AI_URL"), "/")
	if aiBase == "" {
		aiBase = "http://127.0.0.1:8000"
	}
	port := os.Getenv("PORT")
	if port == "" {
		port = "8787"
	}
	g := &Gateway{
		AIBase: aiBase,
		Client: &http.Client{Timeout: 10 * time.Minute},
	}
	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		resp, err := g.Client.Get(g.AIBase + "/health")
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadGateway)
			return
		}
		defer resp.Body.Close()
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(resp.StatusCode)
		_, _ = io.Copy(w, resp.Body)
	})
	http.HandleFunc("/v1/chat", func(w http.ResponseWriter, r *http.Request) {
		g.proxy("/v1/chat", w, r)
	})
	http.HandleFunc("/v1/image", func(w http.ResponseWriter, r *http.Request) {
		g.proxy("/v1/image", w, r)
	})
	http.HandleFunc("/v1/info", func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{
			"name": "S.AI Core Gateway",
			"free": true,
			"local": true,
			"backend": g.AIBase,
		})
	})
	log.Printf("S.AI gateway listening on :%s -> %s", port, g.AIBase)
	log.Fatal(http.ListenAndServe(":"+port, nil))
}

using System;
using System.IO;
using System.Net.Http;
using System.Threading.Tasks;
using CloudinaryDotNet;
using CloudinaryDotNet.Actions;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

using Dumble.BundleManagementService.Application.Contracts;

namespace Dumble.BundleManagementService.Infrastructure.Blobs
{
    public sealed class CloudinaryFileService : IFileService
    {
        private readonly Cloudinary _cloudinary;
        private readonly string? _folder;
        private readonly ILogger<CloudinaryFileService>? _logger;
        private readonly HttpClient _httpClient = new HttpClient();

        public CloudinaryFileService(IConfiguration configuration, ILogger<CloudinaryFileService>? logger = null)
        {
            _logger = logger;
            var section = configuration.GetSection("Cloudinary");
            var cloudName = section["CloudName"] ?? throw new ArgumentException("Cloudinary:CloudName is missing");
            var apiKey = section["ApiKey"] ?? throw new ArgumentException("Cloudinary:ApiKey is missing");
            var apiSecret = section["ApiSecret"] ?? throw new ArgumentException("Cloudinary:ApiSecret is missing");
            _folder = section["Folder"]; // optional

            var account = new Account(cloudName, apiKey, apiSecret);
            _cloudinary = new Cloudinary(account)
            {
                // optional: set secure by default
                Api =
                {
                    Secure = true
                }
            };
        }

        /// <summary>
        /// Upload the provided stream to Cloudinary. Returns the uploaded resource's public id (preferred)
        /// or secure url if you prefer (this implementation returns the PublicId if available, otherwise secure url).
        /// </summary>
        public async Task<string> UploadAsync(Stream fileStream, string fileName, string contentType)
        {
            if (fileStream == null) throw new ArgumentNullException(nameof(fileStream));
            if (string.IsNullOrWhiteSpace(fileName)) throw new ArgumentNullException(nameof(fileName));

            // create a unique public id (keeps original filename readable)
            var nameWithoutExt = Path.GetFileNameWithoutExtension(fileName);
            var ext = Path.GetExtension(fileName);
            var uniqueId = $"{nameWithoutExt}_{Guid.NewGuid():N}";
            var publicId = string.IsNullOrWhiteSpace(_folder) ? uniqueId : $"{_folder}/{uniqueId}";

            // Reset stream position if possible
            if (fileStream.CanSeek) fileStream.Position = 0;

            var uploadParams = new ImageUploadParams()
            {
                File = new FileDescription(fileName, fileStream),
                PublicId = publicId,
            };

            var uploadResult = await _cloudinary.UploadAsync(uploadParams);

            if (uploadResult == null)
            {
                _logger?.LogError("Cloudinary upload returned null for {FileName}", fileName);
                throw new InvalidOperationException("Upload to Cloudinary failed.");
            }

            if (uploadResult.Error != null)
            {
                _logger?.LogError("Cloudinary upload error: {Error}, details: {Details}", uploadResult.Error.Message, uploadResult.Error.Message);
                throw new InvalidOperationException($"Cloudinary upload error: {uploadResult.Error.Message}");
            }

            return uploadResult.PublicId ?? uploadResult.SecureUrl?.ToString() ?? publicId;
        }

        public async Task<Stream> DownloadAsync(string filePath)
        {
            if (string.IsNullOrWhiteSpace(filePath)) throw new ArgumentNullException(nameof(filePath));

            var publicId = ExtractPublicId(filePath);

            try
            {
                // get resource info from Cloudinary to obtain a secure url
                var getParams = new GetResourceParams(publicId)
                {
                    ResourceType = ResourceType.Auto
                };

                var resource = await _cloudinary.GetResourceAsync(getParams);
                string? url = resource?.SecureUrl?.ToString();

                if (string.IsNullOrWhiteSpace(url))
                {
                    // fallback: try building URL (may not always work for raw types with extension)
                    url = _cloudinary.Api.UrlImgUp.Secure(true).BuildUrl(publicId);
                }

                if (string.IsNullOrWhiteSpace(url))
                {
                    _logger?.LogWarning("Could not get URL for resource {PublicId}", publicId);
                    throw new FileNotFoundException("Resource not found on Cloudinary.", publicId);
                }

                var response = await _httpClient.GetAsync(url);
                response.EnsureSuccessStatusCode();
                var ms = new MemoryStream();
                await response.Content.CopyToAsync(ms);
                ms.Position = 0;
                return ms;
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Cloudinary exception while downloading {FilePath}", filePath);
                throw;
            }
        }

        /// <summary>
        /// Delete a file by public id or url. Returns true if deletion was successful or resource already not found.
        /// </summary>
        public async Task<bool> DeleteAsync(string filePath)
        {
            if (string.IsNullOrWhiteSpace(filePath)) throw new ArgumentNullException(nameof(filePath));
            var publicId = ExtractPublicId(filePath);

            try
            {
                var delParams = new DeletionParams(publicId)
                {
                    ResourceType = ResourceType.Auto
                };

                var result = await _cloudinary.DestroyAsync(delParams);

                // result.Result can be "ok", "not_found", etc.
                if (result == null)
                {
                    _logger?.LogWarning("Cloudinary deletion returned null for {PublicId}", publicId);
                    return false;
                }

                var res = result.Result;
                if (string.Equals(res, "ok", StringComparison.OrdinalIgnoreCase) ||
                    string.Equals(res, "deleted", StringComparison.OrdinalIgnoreCase) ||
                    string.Equals(res, "not_found", StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }

                _logger?.LogWarning("Cloudinary deletion unexpected result '{Result}' for {PublicId}", res, publicId);
                return false;
            }
            catch (Exception ex)
            {
                _logger?.LogError(ex, "Cloudinary exception while deleting {PublicId}", publicId);
                return false;
            }
        }

        /// <summary>
        /// Check if resource exists on Cloudinary (by public id or url).
        /// </summary>
        public async Task<bool> ExistsAsync(string filePath)
        {
            if (string.IsNullOrWhiteSpace(filePath)) throw new ArgumentNullException(nameof(filePath));
            var publicId = ExtractPublicId(filePath);

            try
            {
                var getParams = new GetResourceParams(publicId)
                {
                    ResourceType = ResourceType.Auto
                };

                var resource = await _cloudinary.GetResourceAsync(getParams);
                return resource != null && !string.IsNullOrWhiteSpace(resource.PublicId);
            }
            catch (Exception ex)
            {
                // Cloudinary throws when not found; treat as missing
                _logger?.LogDebug(ex, "Cloudinary GetResource failed for {PublicId}", publicId);
                return false;
            }
        }

        /// <summary>
        /// Helper: if user passed a full Cloudinary url, try to extract the public id (without extension).
        /// Otherwise, assume the value is already the public id and return as-is.
        /// </summary>
        private string ExtractPublicId(string filePath)
        {
            if (Uri.TryCreate(filePath, UriKind.Absolute, out var uri))
            {
                // Cloudinary urls contain "/v<version>/" then the public id with extension
                // Example: https://res.cloudinary.com/<cloud>/image/upload/v1620000000/folder/myfile.jpg
                // We try to find "/v" segment and take everything after the next '/'
                var segs = uri.AbsolutePath.Split('/', StringSplitOptions.RemoveEmptyEntries);
                // find segment that starts with 'v' followed by digits
                for (int i = 0; i < segs.Length - 1; i++)
                {
                    if (segs[i].Length > 1 && segs[i][0] == 'v' && int.TryParse(segs[i].AsSpan(1), out _))
                    {
                        // public id is remaining segments joined, without extension
                        var remaining = string.Join('/', segs[(i + 1)..]);
                        // remove known extension (last dot)
                        var lastDot = remaining.LastIndexOf('.');
                        if (lastDot > 0) remaining = remaining.Substring(0, lastDot);
                        return Uri.UnescapeDataString(remaining);
                    }
                }

                // fallback: take last segment without extension
                var last = segs.Length > 0 ? segs[^1] : uri.AbsolutePath;
                var dot = last.LastIndexOf('.');
                if (dot > 0) last = last.Substring(0, dot);
                return Uri.UnescapeDataString(last);
            }

            // assume already public id
            return filePath;
        }
    }
}

# {{jreleaserCreationStamp}}
cask "{{brewCaskName}}" do
  desc "{{projectDescription}}"
  homepage "{{projectLinkHomepage}}"
  url "{{distributionUrl}}",
      verified: "{{repoHost}}"
  version "{{projectVersion}}"
  sha256 "{{distributionChecksumSha256}}"
  name "{{brewCaskDisplayName}}"

  app "Partida.app"

  zap trash: [
    "~/.partida",
  ]
end
